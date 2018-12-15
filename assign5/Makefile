JC = javac
J = java
FIND = find
ROOTSERVER = 199.9.14.201
MAIN = edu.wisc.cs.sdn.simpledns.SimpleDNS
CLSPATH = lib/*
SRCPATH = src/
JFLAGS = -cp "$(SRCPATH):$(CLSPATH)"
PFLAGS = -r $(ROOTSERVER) -e ec2.csv
JCFLAGS = -g -sourcepath $(SRCPATH) -classpath $(CLSPATH)

SOURCES = \
	src/edu/wisc/cs/sdn/simpledns/packet/DNSRdata.java \
	src/edu/wisc/cs/sdn/simpledns/packet/DNSRdataAddress.java \
	src/edu/wisc/cs/sdn/simpledns/packet/DNSRdataBytes.java \
	src/edu/wisc/cs/sdn/simpledns/packet/DNSRdataName.java \
	src/edu/wisc/cs/sdn/simpledns/packet/DNSRdataString.java \
	src/edu/wisc/cs/sdn/simpledns/packet/DNSResourceRecord.java \
	src/edu/wisc/cs/sdn/simpledns/packet/DNS.java \
	src/edu/wisc/cs/sdn/simpledns/packet/DNSQuestion.java\
	src/edu/wisc/cs/sdn/simpledns/SimpleDNS.java

.SUFFIXES: .java .class

%.class: %.java
	$(JC) $(JCFLAGS) $<

default: classes 

run:
	$(J) $(JFLAGS) $(MAIN) $(PFLAGS) 

classes: $(SOURCES:.java=.class)

clean:
	rm src/edu/wisc/cs/sdn/simpledns/*.class

cleanall:
	$(FIND) . -name '*.class' -delete
