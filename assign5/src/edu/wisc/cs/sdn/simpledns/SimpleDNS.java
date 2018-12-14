package edu.wisc.cs.sdn.simpledns;

import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*; 

import edu.wisc.cs.sdn.simpledns.packet.DNS;
import edu.wisc.cs.sdn.simpledns.packet.*;

import inet.ipaddr.*;

public class SimpleDNS 
{

	private static String ec2_csv;

	public static void main(String[] args)
	{
		// Check the args
		if (args.length != 4 || !args[0].equals("-r") || !args[2].equals("-e")) {
			System.out.println("bad args");
			return; 
		}
		
		System.out.println("passed argscheck");

		// Get the root server and ec2 csv
		String root_server = null; 
		ec2_csv = null; 
		
        for (int i = 0; i < args.length; i++) {
        	if (args[i] == "-r") {
        		root_server = args[i+1];
        	}
        	if (args[i] == "-e") {
        		ec2_csv = args[i+1]; 
        	}
        }
		
		System.out.println("gonna enter main part");

        // Get the data from the socket
        try {
        	DatagramSocket socket = new DatagramSocket(8053); 
        	InetAddress root_servIP = InetAddress.getByName(root_server); 
        	DatagramPacket packet = new DatagramPacket(new byte[1500], 1500); 
			
			System.out.println("enter inf loop");
        	while (true) {
        		socket.receive(packet);
				
				System.out.println("socket receive");

        		DNS dns = DNS.deserialize(packet.getData(), packet.getLength()); 
				
				System.out.println("crated DNS result");

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
				
				System.out.println("solve the problem");
        		if (dns.isRecursionDesired()) {
					System.out.println("solving recursively");
        			replyPacket = solveRecursively(packet, root_servIP); 
        		} else {
					System.out.println("solving once");
        			replyPacket = solveOnce(packet, root_servIP); 
        		}
        		
        		replyPacket.setAddress(packet.getAddress());
				replyPacket.setPort(packet.getPort());

				System.out.println("socket send");
				socket.send(replyPacket);
        	}
        }
        catch (Exception e) {
			System.out.println("exception here");
        	return; 
        }
	}
	
	private static DatagramPacket solveOnce(DatagramPacket pack, InetAddress root_servIP) throws Exception {
		DatagramSocket socket = new DatagramSocket(); 
		
		DatagramPacket sendPacket = new DatagramPacket(pack.getData(), pack.getLength(), root_servIP, 53); 
		DatagramPacket receivePacket = new DatagramPacket(new byte[2000], 2000); 
		
		socket.send(sendPacket);
		System.out.println("sending packet once");
		socket.receive(receivePacket);
		System.out.println("receiving packet once");
		socket.close();
		System.out.println("closed socket");
		
		return receivePacket; 
	}
	
	private static DatagramPacket solveRecursively(DatagramPacket pack, InetAddress root_servIP) throws Exception {
		DatagramSocket socket = new DatagramSocket(); 
		
		DatagramPacket sendPacket = new DatagramPacket(pack.getData(), pack.getLength(), root_servIP, 53); 
		DatagramPacket receivePacket = new DatagramPacket(new byte[1500], 1500); 
		
		List<DNSResourceRecord> prevAuth = new ArrayList<DNSResourceRecord>(); 
		List<DNSResourceRecord> prevAdditionals = new ArrayList<DNSResourceRecord>(); 
		List<DNSResourceRecord> cnames = new ArrayList<DNSResourceRecord>(); 
		
		socket.send(sendPacket);
		
		while (true) {
			socket.receive(receivePacket);
			
			DNS receiveDNS = DNS.deserialize(receivePacket.getData(), receivePacket.getLength()); 
			
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
				
				if (answer.getType() == DNS.TYPE_CNAME) {
					cnames.add(answer); 
					
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
					
					socket.send(new DatagramPacket(request.serialize(), request.getLength()));
					
					
				} else {
					if (answer.getType() == DNS.TYPE_A) {
						// TODO: Check against EC2 address and append TXT record

						File ec2 = new File(ec2_csv);

						Scanner scnr = new Scanner(ec2);
						scnr.useDelimiter(",");

						int count = 0;

						IPAddress addr = null;

						while(scnr.hasNext()){
							if (count % 2 == 0){
								//even (ie CIDR)
								addr = new IPAddressString(scnr.next()).toAddress();
							}else{
								// odd (ie region)
								String region = scnr.next();

								String ipp = ((DNSRdataAddress)answer.getData()).getAddress().toString();
								System.out.println(ipp);
								// if (addr.contains(new IPAddressString(((DNSRdataAddress)answer.getData()).getAddress().).getAddress())){

								// }

							}
							count++;
						}

					}
					
					for (DNSResourceRecord name: cnames) {
						answers.add(name); 
					}
					
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
					
					socket.close(); 
					
					DatagramPacket response = new DatagramPacket(receiveDNS.serialize(), receiveDNS.getLength()); 
					return response; 
					
				}
				
			} else {
				// We received no answer must check for a different server to ask
				InetAddress diffServ = null; 
				
				first: 
				for (DNSResourceRecord au : author) {
					for (DNSResourceRecord addi : addit) {
						String servName = ((DNSRdataName) au.getData()).getName(); 
						if (au.getType() == DNS.TYPE_NS && addi.getType() == DNS.TYPE_A && addi.getName() == servName) {
							diffServ = ((DNSRdataAddress) addi.getData()).getAddress(); 
							break first; 
						}
					}
				}
				
				if (diffServ == null) {
					// Admit defeat
					DNS response = new DNS(); 
					
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
					
					socket.close();
					
					return new DatagramPacket(response.serialize(), response.getLength()); 
					
					
				} else {
					socket.send(new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), diffServ, 53));
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
