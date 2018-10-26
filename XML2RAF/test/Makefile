all: 1jmc.raf 1jmc.chains test

clean:
	rm 1jmc.raf 1jmc.chains

1jmc.raf: 1jmc.xml.gz
	xml2raf1.pl 1jmc.xml.gz 1jmc.raf

1jmc.chains: 1jmc.xml.gz
	getChains.pl 1jmc.xml.gz 1jmc.chains

test: 1jmc.chains 1jmc.raf 1jmc.chains.correct 1jmc.raf.correct
	diff 1jmc.chains.correct 1jmc.chains 
	diff 1jmc.raf.correct 1jmc.raf 
