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

	// values read in from ec2 file
	private static List<String> ec2Addresses = new ArrayList<>();
	private static List<String> ec2Regions = new ArrayList<>();

	public static final short TYPE_TXT = 16;

	// enable debug statements
	private static boolean debug = false;

	public static void main(String[] args)
	{
		// Check the args
		if (args.length != 4 || !args[0].equals("-r") || !args[2].equals("-e")) {
			System.out.println("bad args. exiting...");
			return; 
		}

		// Get the root server and ec2 csv
		root_server = args[1];
		ec2_csv = args[3];
		
		Scanner scnr = null;
		try {
			// Saves ec2 info into lists for quick access (assumes well formatted file)
			File f = new File(ec2_csv);
			scnr = new Scanner(f);

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
		} finally{
			if (scnr != null) scnr.close();
		}


		// Get the data from the socket
		DatagramSocket socket = null;
        try {
        	socket = new DatagramSocket(8053); 
        	root_servIP = InetAddress.getByName(root_server); 
        	DatagramPacket packet = new DatagramPacket(new byte[1500], 1500); 
			
			//System.out.println("enter inf loop");
        	while (true) {
        		socket.receive(packet);
				
				if (debug) System.out.println("socket receive");

				DNS dns = DNS.deserialize(packet.getData(), packet.getLength()); 

				//System.out.println(dns.toString());

        		// Check the opcode for only the standard (0)
        		if (dns.getOpcode() != DNS.OPCODE_STANDARD_QUERY) {
					if (debug) System.out.println("skip loop: bad opcode");
        			continue; 
        		}
        		
        		// Check the query type
        		if (dns.getQuestions().isEmpty() || !checkQueryType(dns.getQuestions().get(0).getType())) {
					if (debug) System.out.println("skip loop: bad query");
        			continue; 
        		}

				DatagramPacket replyPacket;
        		if (dns.isRecursionDesired()) {
					if (debug) System.out.println("solving recursively");
        			replyPacket = solveRecursively(packet, null, true); 
        		} else {
					if (debug) System.out.println("solving once");
        			replyPacket = solveOnce(packet); 
        		}
        		
        		replyPacket.setAddress(packet.getAddress());
				replyPacket.setPort(packet.getPort());

				if (debug) System.out.println("SENDING RESULTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTT");
				socket.send(replyPacket);
        	}
        }catch (Exception e) {
			e.printStackTrace(); 
        } finally{
			if (socket != null && !socket.isClosed()) socket.close();
		}
		return;
	}
	
	private static DatagramPacket solveOnce(DatagramPacket pack) throws Exception {
		DatagramSocket socket = new DatagramSocket();
		DatagramPacket sendPacket = new DatagramPacket(pack.getData(), pack.getLength(), root_servIP, 53); 
		DatagramPacket receivePacket = new DatagramPacket(new byte[1500], 1500);

		if (debug) System.out.println("sending packet once");	
		socket.send(sendPacket);
		if (debug) System.out.println("receiving packet once");
		socket.receive(receivePacket);

		if (debug) {
			DNS receiveDNS = DNS.deserialize(receivePacket.getData(), receivePacket.getLength());
			System.out.println(receiveDNS.toString());
		}

		socket.close();

		return receivePacket; 
	}
	
	private static DatagramPacket solveRecursively(DatagramPacket pack, DatagramSocket socket, boolean top) throws Exception {

		if (debug) System.out.println("Start recursive search");

		// only create socket if one doesnt exist yet (to deal w/ recursive calls to this method)
		if (socket == null){
			socket = new DatagramSocket();
		}

		DatagramPacket sendPacket = new DatagramPacket(pack.getData(), pack.getLength(), root_servIP, 53); 
		DatagramPacket receivePacket;
		
		List<DNSResourceRecord> prevAuth = new ArrayList<DNSResourceRecord>(); 
		List<DNSResourceRecord> prevAdditionals = new ArrayList<DNSResourceRecord>(); 
		List<DNSResourceRecord> cnames = new ArrayList<DNSResourceRecord>(); 
		
		socket.send(sendPacket);

		// save this packet status for reasons I forgot
		DatagramPacket ogSend = sendPacket;
		
		while (true) {
			if (debug) System.out.println("receiving new packet");
			receivePacket = new DatagramPacket(new byte[1500], 1500);
			socket.receive(receivePacket);
			
			DNS receiveDNS = DNS.deserialize(receivePacket.getData(), receivePacket.getLength()); 
			
			if (debug) System.out.println(receiveDNS.toString());

			List<DNSResourceRecord> author = receiveDNS.getAuthorities(); 
			List<DNSResourceRecord> addit = receiveDNS.getAdditional(); 
			List<DNSResourceRecord> answers = receiveDNS.getAnswers(); 

			// Check if there are any authorities of correct type
			if (author.size()> 0) {
				for (DNSResourceRecord rec : author) {
					if (checkQueryType(rec.getType())) {
						prevAuth = author; 
						break; 
					}
				}
			}
			
			// Check if there are any additionals
			if (addit.size() > 0) {
				prevAdditionals = addit; 
			}
			
			// Check and react to the answers
			if (answers.size() > 0) {
				if (debug) System.out.println("using answers (ie answers.size != 0");

				// default to 1st answer (could add rr later)
				DNSResourceRecord answer = answers.get(0); 

				// if CNAME need to do lookup on the data name
				if (answer.getType() == DNS.TYPE_CNAME) {
					cnames.add(answer); 
					
					if (debug) System.out.println("got cname so redoing calls from the root server on cname data");

					// send request for the CNAME address
					DNSRdataName cname = (DNSRdataName) answer.getData();
					sendPacket = buildQueryPacket(receiveDNS, cname, pack, root_servIP);

					socket.send(sendPacket);
					
				} else {
					if (answer.getType() == DNS.TYPE_A) {

						if (debug) System.out.println("got A record result, checking against EC2");

						// check for match in the arrays holding ip/region values
						for (int i = 0; i < ec2Addresses.size(); i++){
							IPAddress ipaddr = new IPAddressString(ec2Addresses.get(i)).toAddress();

							String ip = ((DNSRdataAddress)answer.getData()).getAddress().getHostAddress();
							String region = ec2Regions.get(i);
							if (ipaddr.contains(new IPAddressString(ip).toAddress())){
								if (debug) System.out.println("our ip matches EC2 w/ region " + region);
								answers.add(new DNSResourceRecord(answer.getName(), TYPE_TXT, new DNSRdataString(region + "-" + ip)));
								break;
							}
						}
					}
					
					// add cnames to answers in result
					for (DNSResourceRecord name: cnames) {
						answers.add(name); 
					}
					
					// not sure if these are needed
					// not including these would mean that the auth/addit sections on the result would be empty
					// standard dig doesnt return any auth/addit but Ill leave for now
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

					// if top level call, close socket before returning
					if (top) socket.close(); 
					
					if (debug) System.out.println("return from recursive search w/ valid record");

					DatagramPacket response = new DatagramPacket(receiveDNS.serialize(), receiveDNS.getLength()); 
					return response; 
					
				}
				
			} else {
				// We received no answer must check for a different server to ask
				InetAddress diffServ = null; 
				
				if (debug) System.out.println("no answers so have to go to authorities");

				// match auths w/ additionals
				// no equals for DNSResource record so have to check manually
				first: 
				for (DNSResourceRecord au : author) {
					if (au.getType() == DNS.TYPE_NS){
						String servName = ((DNSRdataName) au.getData()).getName(); 
						for (DNSResourceRecord addi : addit){
							if (addi.getType() == DNS.TYPE_A && addi.getName().equals(servName)){
								diffServ = ((DNSRdataAddress) addi.getData()).getAddress(); 
								if (debug) System.out.println("using diffserv " + diffServ.toString());
								break first; 
							}
						}
					}
				}

				if (diffServ == null) {
					DNS response = new DNS(); 
					
					if (debug) System.out.println("got null diffserv");

					// If there is no match between auths/addits, recursively search on one of the auth names
					// Done by calling solveRecursively again, to get the resulting ip of said auth
					// After that, pass the original query to the new IP you got to continue the search
					if(author.size() != 0){
						// Send another request with the cname
						DNSRdataName aname = (DNSRdataName) author.get(0).getData();

						if (debug) System.out.println("got auth w/o additional. recursing on " + aname.getName());

						DatagramPacket sendLocalPacket = buildQueryPacket(receiveDNS, aname, pack, root_servIP);

						// recursive call to find IP of the auth name
						// need to pass in the open socket & say its not the top level call
						DatagramPacket result = solveRecursively(sendLocalPacket,socket, false);
	
						// parse results
						DNS resultDNS = DNS.deserialize(result.getData(), result.getLength());
						DNSRdataAddress ip = (DNSRdataAddress)resultDNS.getAnswers().get(0).getData();

						// send original query to the new auth server (w/ IP)
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
					
						// if top level call, close socket before returning
						if (top) socket.close();
					
						if (debug) System.out.println("return from recursive search w/o good record");

						return new DatagramPacket(response.serialize(), response.getLength()); 
					}

					
				} else {
					// just forward to request to the next address
					DatagramPacket nextPacket = new DatagramPacket(sendPacket.getData(), sendPacket.getLength(), diffServ, 53);
					
					if (debug) {
						DNS dd = DNS.deserialize(nextPacket.getData(), nextPacket.getLength());
						System.out.println(dd.toString());
					}

					socket.send(nextPacket);
				}
			}
			
		}
		
	}
	
	// validate types
	private static boolean checkQueryType(short type) {
		
		if (type == DNS.TYPE_A || type == DNS.TYPE_AAAA || type == DNS.TYPE_NS|| type == DNS.TYPE_CNAME) {
			return true;
		} else {
			return false; 
		}
	}

	// build request query on name
	private static DatagramPacket buildQueryPacket(DNS receiveDNS, DNSRdataName name, DatagramPacket pack, InetAddress ip){

		List<DNSQuestion> questions = new ArrayList<DNSQuestion>(); 
		questions.add(new DNSQuestion(name.getName(), receiveDNS.getQuestions().get(0).getType())); 
		
		DNS request = new DNS(); 
		request.setQuery(true);
		request.setQuestions(questions);
		request.setTruncated(false);
		request.setRecursionDesired(true);
		request.setId(DNS.deserialize(pack.getData(), pack.getLength()).getId());
		
		if (debug) System.out.println("building REQUEST\n" + request.toString());

		return new DatagramPacket(request.serialize(), request.getLength(), ip, 53);
	}
}
