package edu.wisc.cs.sdn.simpledns;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;

import edu.wisc.cs.sdn.simpledns.packet.*;

import inet.ipaddr.*;

public class SimpleDNS 
{

	private static String ec2_csv;
	private static String root_server;
	private static InetAddress root_servIP;

	private static List<String> ec2Addresses = new ArrayList<>();
	private static List<String> ec2Regions = new ArrayList<>();

	public static final short TYPE_TXT = 16;

	public static void main(String[] args)
	{
		// Check the args
		if (args.length != 4 || !args[0].equals("-r") || !args[2].equals("-e")) {
			System.out.println("bad args");
			return; 
		}

		// Get the root server and ec2 csv
		root_server = args[1];
		ec2_csv = args[3];
		
		System.out.println(root_server + "\n" + ec2_csv);


		try {
			// Saves ec2 info into lists for quick access (assumes well formatted file)
			File f = new File(ec2_csv);
			Scanner scnr = new Scanner(f);

			while(scnr.hasNextLine()){
				String line = scnr.nextLine();
				String[] sp = line.split(",");

				if (sp.length == 2){
					ec2Addresses.add(sp[0]);
					ec2Regions.add(sp[1]);
				}
			}
		} catch (FileNotFoundException e){
			System.out.println("couldnt find ec2 file " + ec2_csv + ". exiting...");
			return;
		}


        // Get the data from the socket
        try {
        	DatagramSocket socket = new DatagramSocket(8053); 
        	root_servIP = InetAddress.getByName(root_server); 
        	DatagramPacket packet = new DatagramPacket(new byte[1500], 1500); 
			
			System.out.println("enter inf loop");
        	while (true) {
        		socket.receive(packet);
				
				System.out.println("socket receive");



        		DNS dns = DNS.deserialize(packet.getData(), packet.getLength()); 
				//System.out.println(dns.toString());

        		// Check the opcode for only the standard (0)
        		if (dns.getOpcode() != DNS.OPCODE_STANDARD_QUERY) {
					System.out.println("skip loop: bad opcode");
        			continue; 
        		}
        		
        		// Check the query type
        		if (dns.getQuestions().isEmpty() || !checkQueryType(dns.getQuestions().get(0).getType())) {
					System.out.println("skip loop: bad query");
        			continue; 
        		}
        		
        		DatagramPacket replyPacket; 

        		if (dns.isRecursionDesired()) {
					System.out.println("solving recursively");
        			replyPacket = solveRecursively(packet,null, true); 
        		} else {
					System.out.println("solving once");
        			replyPacket = solveOnce(packet); 
        		}
        		
        		replyPacket.setAddress(packet.getAddress());
				replyPacket.setPort(packet.getPort());

				System.out.println("SENDING RESULTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTT");
				socket.send(replyPacket);
        	}
        }
        catch (Exception e) {
			e.printStackTrace();
        	return; 
        }
	}
	
	private static DatagramPacket solveOnce(DatagramPacket pack) throws Exception {
		DatagramSocket socket = new DatagramSocket();

		DatagramPacket sendPacket = new DatagramPacket(pack.getData(), pack.getLength(), root_servIP, 53); 
		DatagramPacket receivePacket = new DatagramPacket(new byte[1500], 1500);

		System.out.println("sending packet once");	
		socket.send(sendPacket);
		System.out.println("receiving packet once");
		socket.receive(receivePacket);
		
		System.out.println("never receieve");

		DNS receiveDNS = DNS.deserialize(receivePacket.getData(), receivePacket.getLength());
		System.out.println(receiveDNS.toString());

		socket.close();

		return receivePacket; 
	}
	
	private static DatagramPacket solveRecursively(DatagramPacket pack, DatagramSocket socket, boolean top) throws Exception {

		System.out.println("CALLINGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGG");

		if (socket == null){
			socket = new DatagramSocket();
		}

		DatagramPacket sendPacket = new DatagramPacket(pack.getData(), pack.getLength(), root_servIP, 53); 
		DatagramPacket receivePacket = null;//new DatagramPacket(new byte[1500], 1500); 
		
		List<DNSResourceRecord> prevAuth = new ArrayList<DNSResourceRecord>(); 
		List<DNSResourceRecord> prevAdditionals = new ArrayList<DNSResourceRecord>(); 
		List<DNSResourceRecord> cnames = new ArrayList<DNSResourceRecord>(); 
		
		socket.send(sendPacket);

		// save this packet status for reasons I forgot
		DatagramPacket ogSend = sendPacket;
		
		while (true) {
			System.out.println("receiving new packet");
			receivePacket = new DatagramPacket(new byte[1500], 1500);
			socket.receive(receivePacket);
			
			DNS receiveDNS = DNS.deserialize(receivePacket.getData(), receivePacket.getLength()); 
			
			System.out.println(receiveDNS.toString());

			List<DNSResourceRecord> author = receiveDNS.getAuthorities(); 
			List<DNSResourceRecord> addit = receiveDNS.getAdditional(); 
			List<DNSResourceRecord> answers = receiveDNS.getAnswers(); 

			// Check if there are any additionals
			if (author.size()> 0) {
				for (DNSResourceRecord rec : author) {
					if (checkQueryType(rec.getType())) {
						prevAuth = author; 
						break; 
					}
				}
			}
			
			// Check if there are any authorities
			if (addit.size() > 0) {
				prevAdditionals = addit; 
			}
			
			// Check and react to the answers
			if (answers.size() > 0) {
				DNSResourceRecord answer = answers.get(0); 
				
				System.out.println("using answers");

				if (answer.getType() == DNS.TYPE_CNAME) {
					cnames.add(answer); 
					
					System.out.println("got cname so redoing calls from the root server on cname data");

					// Send another request with the cname
					DNSRdataName cname = (DNSRdataName) answer.getData();
					List<DNSQuestion> questions = new ArrayList<DNSQuestion>(); 
					questions.add(new DNSQuestion(cname.getName(), receiveDNS.getQuestions().get(0).getType())); 
					
					DNS request = new DNS(); 
					request.setQuery(true);
					request.setQuestions(questions);
					request.setTruncated(false);
					request.setRecursionDesired(true);
					request.setId(DNS.deserialize(pack.getData(), pack.getLength()).getId());
					
					System.out.println("sending CNAME REQUEST\n" + request.toString());

					sendPacket = new DatagramPacket(request.serialize(), request.getLength(), root_servIP, 53);

					socket.send(sendPacket);
					
				} else {
					if (answer.getType() == DNS.TYPE_A) {
						// TODO: Check against EC2 address and append TXT record

						System.out.println("got A record result");

						for (int i = 0; i < ec2Addresses.size(); i++){
							IPAddress ipaddr = new IPAddressString(ec2Addresses.get(i)).toAddress();

							String ip = ((DNSRdataAddress)answer.getData()).getAddress().getHostAddress();
							String region = ec2Regions.get(i);
							if (ipaddr.contains(new IPAddressString(ip).toAddress())){
								System.out.println("our ip matches EC2 w/ region " + region);
								answers.add(new DNSResourceRecord(answer.getName(), TYPE_TXT, new DNSRdataString(region + "-" + ip)));
								break;
							}
						}
					}
					
					for (DNSResourceRecord name: cnames) {
						answers.add(name); 
					}
					
					// not sure if these are needed
					// not including these would mean that the auth/addit sections on the result would be empty
					if (receiveDNS.getAuthorities().size() == 0) {
						receiveDNS.setAuthorities(prevAuth);
					}
					if (receiveDNS.getAdditional().size() == 0)	{
						receiveDNS.setAdditional(prevAdditionals);
					}
					
					receiveDNS.setAnswers(answers);
					receiveDNS.setQuery(false);
					receiveDNS.setRcode((byte)0);
					receiveDNS.setCheckingDisabled(false);
					receiveDNS.setAuthenicated(false);
					receiveDNS.setRecursionAvailable(true);
					receiveDNS.setTruncated(false); 
					receiveDNS.setAuthoritative(false); 
					receiveDNS.setOpcode((byte)0);
					
					// have to rewrite the question in case a CNAME was followed, otherwise dig gets mad
					DNS ogDNS = DNS.deserialize(pack.getData(), pack.getLength());
					receiveDNS.setQuestions(ogDNS.getQuestions());

					if (top) socket.close(); 
					
					System.out.println("RETURNINGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGG");

					DatagramPacket response = new DatagramPacket(receiveDNS.serialize(), receiveDNS.getLength()); 
					return response; 
					
				}
				
			} else {
				// We received no answer must check for a different server to ask
				InetAddress diffServ = null; 
				
				System.out.println("no answers so have to go to authorities");

				first: 
				for (DNSResourceRecord au : author) {
					for (DNSResourceRecord addi : addit) {
						String servName = ((DNSRdataName) au.getData()).getName(); 
						System.out.println(servName + "/" + addi.getName());
						if (au.getType() == DNS.TYPE_NS && addi.getType() == DNS.TYPE_A && addi.getName().equals(servName)) {
							diffServ = ((DNSRdataAddress) addi.getData()).getAddress(); 
							System.out.println("using diffserv " + diffServ.toString());
							break first; 
						}
					}
				}

				if (diffServ == null) {
					DNS response = new DNS(); 
					
					System.out.println("got null diffserv");

					// If there is no match between auths/addits, recursively search on one of the auth names
					// Done by calling solveRecursively again, to get the resulting ip of said auth
					// After that, pass the original query to the new IP you got to continue the search
					if(author.size() != 0){
						System.out.println("got auth w/o additional. need to recurse here");

						// Send another request with the cname
						DNSRdataName aname = (DNSRdataName) author.get(0).getData();
						List<DNSQuestion> questions = new ArrayList<DNSQuestion>(); 
						questions.add(new DNSQuestion(aname.getName(), receiveDNS.getQuestions().get(0).getType())); 
					
						DNS request = new DNS(); 
						request.setQuery(true);
						request.setQuestions(questions);
						request.setTruncated(false);
						request.setRecursionDesired(true);
						request.setId(DNS.deserialize(pack.getData(), pack.getLength()).getId());
					
						System.out.println("sending ANAME REQUEST\n" + request.toString());

						DatagramPacket sendLocalPacket = new DatagramPacket(request.serialize(), request.getLength(), root_servIP, 53);

						DatagramPacket result = solveRecursively(sendLocalPacket,socket, false);

						
						DNS resultDNS = DNS.deserialize(result.getData(), result.getLength());
						DNSRdataAddress ip = (DNSRdataAddress)resultDNS.getAnswers().get(0).getData();
						

						sendPacket = new DatagramPacket(sendPacket.getData(), sendPacket.getLength(), ip.getAddress(), 53);

						socket.send(sendPacket);

					}else{

						// actually out of options here
						// not sure what you had this code doing here

						// Add the cnames
						for (DNSResourceRecord cname : cnames) {
							answers.add(cname); 
						}
					
						response.setAnswers(answers);
						response.setQuestions(DNS.deserialize(pack.getData(), pack.getLength()).getQuestions()); 
					
						// Remove bad types
						Collection<DNSResourceRecord> remove = new ArrayList<DNSResourceRecord>();
						for (DNSResourceRecord auth : author) {
							if (!checkQueryType(auth.getType()))
								remove.add(auth);
						}
						author.removeAll(remove);

						if (author.size() == 0) {
							author = prevAuth; 
						}

						remove.clear();

						for (DNSResourceRecord addi : addit) {
							if (!checkQueryType(addi.getType()))
								remove.add(addi);
						}
						addit.removeAll(remove);
					
						if (addit.size() == 0) {
							addit = prevAdditionals; 
						}

						remove.clear(); 
					
					
						response.setAuthorities(author);
						response.setAdditional(addit);
						response.setQuery(false);
						response.setOpcode((byte)0);
						response.setRcode((byte)0); 
						response.setId(DNS.deserialize(pack.getData(), pack.getLength()).getId());
					
						if (top) socket.close();
					
						return new DatagramPacket(response.serialize(), response.getLength()); 
					}

					
				} else {
					DatagramPacket nextPacket = new DatagramPacket(sendPacket.getData(), sendPacket.getLength(), diffServ, 53);
					DNS dd = DNS.deserialize(nextPacket.getData(), nextPacket.getLength());
					System.out.println(dd.toString());

					socket.send(nextPacket);
				}
			}
			
		}
		
	}
	
	private static boolean checkQueryType(short type) {
		
		if (type == DNS.TYPE_A || type == DNS.TYPE_AAAA || type == DNS.TYPE_NS|| type == DNS.TYPE_CNAME) {
			return true;
		} else {
			return false; 
		}
	}
}
