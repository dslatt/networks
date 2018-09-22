JFLAGS = -g
JC = javac
FIND = find

.SUFFIXES: .java .class

.java.class:
	$(JC) $(JFLAGS) $*.java

CLASSES = \
	Client.java \
	Server.java \
        Iperfer.java 

default: clean classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class 

cleanall:
	$(FIND) . -name '*.class' -delete
