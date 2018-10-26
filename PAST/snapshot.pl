#!/usr/bin/perl
#
#   snapshot.pl - making a snapshot of the PDB with today's date.
#
#   Copyright (C) 2001-2018 The Regents of the University of California
#
#   This program is free software; you can redistribute it and/or modify it
#   under the terms of the GNU General Public License as published by the
#   Free Software Foundation; either version 2, or (at your option) any
#   later version.
#
#   This program is distributed in the hope that it will be useful,
#   but WITHOUT ANY WARRANTY; without even the implied warranty of
#   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#   GNU General Public License for more details.
#
#   You should have received a copy of the GNU General Public License
#   along with this program; if not, write to the Free Software
#   Foundation, 675 Mass Ave, Cambridge, MA 02139, USA.
#
#
# This program is part of the PAST (PDB Archival Snapshot Toolkit) package.
#
# Contact:
# scope@compbio.berkeley.edu
#
# Before you start:
# Make sure the date is correct before running.
# Set $root to the root of the pdb directory.
# If you want to update an existing snapshot, the current snapshot
#  (snapshot/files) should be in $root/data/latest/

use strict;
use Net::FTP;

# ################## config here ########################

# where is pdb snapshot directory on this machine?
my $root = "/lab/db/pdb";

# needs space on temp directory
my $tmpDir = "/tmp";

# where to get pdb files?
my $pdbHostname = "ftp.wwpdb.org";
my $pdbRoot = "/pub/pdb/";
my $pdbIndexRoot = "/pub/pdb/derived_data";
my $username = "anonymous";
my $password = "my_email_address\@my_host_name";

# executable names
my $formatdb = "/lab/bin/formatdb";
my $wget = "/usr/bin/wget";
# also needs:  gzip, ls, cat in your path

# ################## end of config section ###############

my $month0;
my $year4;
my $lastyear4;
our $num_items = 7;

my %month2num = qw(
    jan 1  feb 2  mar 3  apr 4  may 5  jun 6
    jul 7  aug 8  sep 9  oct 10 nov 11 dec 12
    );

sub processLSLR;

MAIN : {
    # check that executables exist
    die ("Can't find wget\n") if (! -x "$wget");
    die ("Can't find formatdb\n") if (! -x "$formatdb");

    # make sure tmp exists
    die ("tmpDir not configured to a writeable directory\n") if 
	(! -w "$tmpDir") || (! -d "$tmpDir");

    # check that the pdb snapshot directory is OK
    my $firstRun = 0;
    if (! -l "$root/data/latest") {
	if (-d "$root/data/latest") {
	    die ("$root/data/latest must be a symlink, not a directory.\nIf you're running this for the first time, please delete the directory and\nrun this program again.\n");
	}

	my $question = "Can't find pdb snapshot directory $root/data/latest
\t Create a new snapshot directory? (c)
\t or Abort (and modify the configuration in snapshot.pl)? (a)\n";
	my $answer = "";
	while ( ($answer ne "C") and ($answer ne "A") )
	{
	    print $question;
	    my $line = <STDIN>;
	    $answer = uc substr $line, 0, 1;
	}
	die "\n" if ($answer eq "A");

	# create new snapshot directory
	mkdir("$root", 0755) if not -e $root;
	mkdir("$root/data", 0755) if not -e "$root/data";
	mkdir("$root/blastdb", 0755) if not -e "$root/blastdb";
	chdir("$root");
	symlink("data/latest/snapshot/seq", "seq") or die ("Couldn't make symlink in $root\n");
	symlink("data/latest/snapshot/all", "all") or die ("Couldn't make symlink in $root\n");
	symlink("data/latest/snapshot/hash", "hash") or die ("Couldn't make symlink in $root\n");
	symlink("data/latest/snapshot/obs-all", "obs-all") or die ("Couldn't make symlink in $root\n");
	symlink("data/latest/snapshot/obs-hash", "obs-hash") or die ("Couldn't make symlink in $root\n");
	symlink("data/latest/snapshot/cif-all", "cif-all") or die ("Couldn't make symlink in $root\n");
	symlink("data/latest/snapshot/cif-hash", "cif-hash") or die ("Couldn't make symlink in $root\n");
	symlink("data/latest/snapshot/cif-obs-all", "cif-obs-all") or die ("Couldn't make symlink in $root\n");
	symlink("data/latest/snapshot/cif-obs-hash", "cif-obs-hash") or die ("Couldn't make symlink in $root\n");
	symlink("data/latest/snapshot/xml-all", "xml-all") or die ("Couldn't make symlink in $root\n");
	symlink("data/latest/snapshot/xml-hash", "xml-hash") or die ("Couldn't make symlink in $root\n");
	symlink("data/latest/snapshot/xml-obs-all", "xml-obs-all") or die ("Couldn't make symlink in $root\n");
	symlink("data/latest/snapshot/xml-obs-hash", "xml-obs-hash") or die ("Couldn't make symlink in $root\n");
	symlink("data/latest/snapshot/bundle-all", "bundle-all") or die ("Couldn't make symlink in $root\n");
	symlink("data/latest/snapshot/bundle-hash", "bundle-hash") or die ("Couldn't make symlink in $root\n");
	chdir("$root/data");

	$firstRun = 1;
    }

    # upgrade from older versions not supporting bundles
    if (! -l "$root/bundle-all") {
	chdir("$root");
	symlink("data/latest/snapshot/bundle-all", "bundle-all") or die ("Couldn't make symlink in $root\n");
	symlink("data/latest/snapshot/bundle-hash", "bundle-hash") or die ("Couldn't make symlink in $root\n");
    }

    # what's the current date?
    my @date = localtime(time());
    $year4 = $date[5] + 1900;
    $lastyear4 = $date[5] + 1899;
    my $year2 = $year4 % 100;
    $year2 = "0"."$year2" unless length($year2) == 2;
    $month0 = $date[4];  $month0++; # zero indexed, so let's add one
    my $month = $month0;
    $month = "0"."$month" unless length($month) == 2;
    my $day = $date[3];
    $day = "0"."$day" unless length($day) == 2;
    my $newdir = "$year2"."$month"."$day";

    chdir("$root/data/");

    if (-e "$newdir") {
	die "Directory for today's date already exists; remove first!\n";
    }

    # what is old dir?
    my $aref;
    my @oldDates;
    my $olddir;
    my $resetTime = 10;
    if (! $firstRun) {
	$olddir = readlink("latest");

	# what are our current files?
	chdir("$olddir/snapshot") or 
	    die "Couldn't cd to latest snapshot directory: $olddir\n";

	# make ls-lR of current directory, following symlinks
	system ("ls -lLR *hash > $tmpDir/tmpOldLSLR");

	# get dates from this file
	$aref = processLSLR("$tmpDir/tmpOldLSLR");
	unlink ("$tmpDir/tmpOldLSLR");
	@oldDates = @$aref;
    }

    my $ftp = Net::FTP->new($pdbHostname, Passive=>1) or 
	die ("Couldn't connect to $pdbHostname");
    $ftp->login($username, $password) or
	die ("Couldn't log in to $pdbHostname");
    $ftp->binary();
    $ftp->cwd($pdbRoot) or
	die ("Couldn't find directory $pdbRoot on $pdbHostname");
    $ftp->get("ls-lR", "$tmpDir/tmpNewLSLR") or
	die ("Couldn't get directory listing from $pdbHostname");

    # get dates from this file
    $aref = processLSLR("$tmpDir/tmpNewLSLR");
    unlink ("$tmpDir/tmpNewLSLR");
    my @newDates = @$aref;

    # set up dirs for this to go in
    chdir("$root/data");
    mkdir("$newdir", 0755) or 
	die ("Couldn't make new data directory $newdir");
    mkdir("$newdir/files", 0755);
    mkdir("$newdir/files/hash", 0755);
    mkdir("$newdir/files/obs-hash", 0755);
    mkdir("$newdir/files/cif-hash", 0755);
    mkdir("$newdir/files/cif-obs-hash", 0755);
    mkdir("$newdir/files/xml-hash", 0755);
    mkdir("$newdir/files/xml-obs-hash", 0755);
    mkdir("$newdir/files/bundle-hash", 0755);
    mkdir("$newdir/files/index", 0755);
    mkdir("$newdir/files/seq", 0755);
    mkdir("$newdir/snapshot", 0755);
    mkdir("$newdir/snapshot/hash", 0755);
    mkdir("$newdir/snapshot/obs-hash", 0755);
    mkdir("$newdir/snapshot/cif-hash", 0755);
    mkdir("$newdir/snapshot/cif-obs-hash", 0755);
    mkdir("$newdir/snapshot/xml-hash", 0755);
    mkdir("$newdir/snapshot/xml-obs-hash", 0755);
    mkdir("$newdir/snapshot/all", 0755);
    mkdir("$newdir/snapshot/obs-all", 0755);
    mkdir("$newdir/snapshot/cif-all", 0755);
    mkdir("$newdir/snapshot/cif-obs-all", 0755);
    mkdir("$newdir/snapshot/xml-all", 0755);
    mkdir("$newdir/snapshot/xml-obs-all", 0755);
    mkdir("$newdir/snapshot/bundle-all", 0755);
    mkdir("$newdir/snapshot/bundle-hash", 0755);
    
    # process the data, getting files with different dates, new
    # files, and indicating the filename of old files as "deletion"
    # also, for all entries in the "newDates" hash, make links
    # to the appropriate files directory.

    my ($i, $oldSnap, $pdbLoc, $newLoc, $newSnap, $entry, $init, $ext);
    for ($i=0; $i<2*$num_items; $i++) {
	if (($i % $num_items)==0) {
	    $newLoc = "$newdir/files/hash/";
	    $pdbLoc = "data/structures/divided/pdb/";
	    $init = "pdb";
	    $ext = ".ent.gz";
	}
	elsif (($i % $num_items)==1) {
	    $newLoc = "$newdir/files/obs-hash/";
	    $pdbLoc = "data/structures/obsolete/pdb/";
	    $init = "pdb";
	    $ext = ".ent.gz";
	}
	elsif (($i % $num_items)==2) {
	    $newLoc = "$newdir/files/cif-hash/";
	    $pdbLoc = "data/structures/divided/mmCIF/";
	    $init = "";
	    $ext = ".cif.gz";
	}
	elsif (($i % $num_items)==3) {
	    $newLoc = "$newdir/files/cif-obs-hash/";
	    $pdbLoc = "data/structures/obsolete/mmCIF/";
	    $init = "";
	    $ext = ".cif.gz";
	}
	elsif (($i % $num_items)==4) {
	    $newLoc = "$newdir/files/xml-hash/";
	    $pdbLoc = "data/structures/divided/XML/";
	    $init = "";
	    $ext = ".xml.gz";
	}
	elsif (($i % $num_items)==5) {
	    $newLoc = "$newdir/files/xml-obs-hash/";
	    $pdbLoc = "data/structures/obsolete/XML/";
	    $init = "";
	    $ext = ".xml.gz";
	}
	else { # ($i % $num_items)==6
	    $newLoc = "$newdir/files/bundle-hash/";
	    $pdbLoc = "compatible/pdb_bundle/";
	    $init = "";
	    $ext = "-pdb-bundle.tar.gz";
	}
	$newSnap = $newLoc;
	$newSnap =~ s/files/snapshot/;
	$oldSnap = $newSnap;
	$oldSnap =~ s/$newdir/$olddir/;

	# handle noc files
	if ($i > $num_items-1) {
	    $ext =~ s/cif/noc/;
	    $ext =~ s/xml/noc/;
	    $ext =~ s/ent/noc/;
	}

	my $href = $newDates[$i];
	my %newD = %$href;
	$href = $oldDates[$i];
	my %oldD;
	%oldD = %$href if (! $firstRun);
	foreach $entry (keys %newD) {
	    next if ($entry eq "populated");

	    my $hashDir = substr($entry,1,2)."/";
	    my $newDate = $newD{$entry};
	    my $oldDate;
	    if (exists $oldD{$entry}) {
		$oldDate = $oldD{$entry};
	    }
	    else {
		$oldDate = "nonexistant";
	    }
	    my $fileName = "$init"."$entry"."$ext";
	    my $snapFile = "$newSnap"."$hashDir"."$fileName";
	    mkdir("$newSnap"."$hashDir", 0755) if (! -d "$newSnap"."$hashDir");
	    my $downloaded = 0;
	    # printf("debug:  entry is $entry\n");
	    # printf("debug:  oldDate is $oldDate\n");
	    # printf("debug:  newDate is $newDate\n");
	    if ($oldDate ne $newDate) {
		# check actual mod times
		my $pdbFile = "$pdbLoc"."$hashDir"."$fileName";
		if ($i == 6) {
		    $pdbFile = "$pdbLoc"."$hashDir"."$entry/"."$fileName";
		}

		my $myFile = "$newLoc"."$hashDir"."$fileName";

		my $newMtime = $ftp->mdtm($pdbFile);
		# printf("debug:  getting modtime\n");
		# printf("debug:  modtime at PDB is $newMtime\n");
		# printf("debug:  myFile is $myFile\n");
		# printf("debug:  pdbFile is $pdbFile\n");
		# printf("debug:  modtime at PDB is $newMtime\n");
		while (!$newMtime) {
		    if ($resetTime > 10000) {
			die("PDB connection closed; couldn't re-open");
		    }
		    # re-open connection
		    sleep($resetTime);
		    $resetTime *= 1.5;
		    print("Resetting FTP connection\n");
		    # system("date");
		    $ftp = Net::FTP->new($pdbHostname, Passive=>1);
		    $ftp->login($username, $password);
		    $ftp->binary();
		    $ftp->cwd($pdbRoot);
		    $newMtime = $ftp->mdtm($pdbFile);
		    # printf("debug:  modtime at PDB is $newMtime\n");
		}
		$resetTime = 10;
		my $oldMtime = 0;
		if (exists $oldD{$entry}) {
		    my $myOldSnap = "$oldSnap"."$hashDir"."$fileName";
		    if (! -e $myOldSnap) {
			$myOldSnap =~ s/\.gz//;
		    }
		    my $myOldFile = "$oldSnap"."$hashDir".readlink($myOldSnap);
		    $oldMtime = (stat($myOldFile))[9];
		}
		# printf("debug:  local modtime is $oldMtime\n");
		if ($oldMtime != $newMtime) {
		    # download the file to new files
		    mkdir("$newLoc"."$hashDir", 0755) if (! -d "$newLoc"."$hashDir");
		    # system("date");
		    printf("getting file $pdbFile\n");
		    my $rc = $ftp->get($pdbFile,$myFile);
		    # printf("debug:  getting file\n");
		    # printf("debug:  pdbFile is $pdbFile\n");
		    # printf("debug:  myFile is $myFile\n");
		    # printf("debug:  rc is $rc\n");
		    while (!$rc) {
			if ($resetTime > 10000) {
			    die("PDB connection closed; couldn't re-open");
			}
			# re-open connection
			sleep($resetTime);
			$resetTime *= 1.5;
			print("Resetting FTP connection\n");
			# system("date");
			$ftp = Net::FTP->new($pdbHostname, Passive=>1);
			$ftp->login($username, $password);
			$ftp->binary();
			$ftp->cwd($pdbRoot);
			$rc = $ftp->get($pdbFile,$myFile);
			# printf("debug:  rc is $rc\n");
		    }
		    $resetTime = 10;

		    # set time on downloaded file
		    utime($newMtime, $newMtime, $myFile);    

		    # link new snapshot to new files
		    symlink ("../../../../".$myFile, $snapFile);
		    $downloaded = 1;
		}
	    }

	    # if we're keeping the old file instead:
	    if ($downloaded==0) {
		# link new snapshot to old files
		my $myOldSnap = "$oldSnap"."$hashDir"."$fileName";
		my $myOldFile = readlink($myOldSnap);
		symlink ($myOldFile, $snapFile);
	    }
		
	    # link 'all' directory to hash directory
	    my $snapAll = "$newSnap"."$fileName";
	    $snapAll =~ s/hash/all/;
	    $snapFile =~ s/$newdir\/snapshot/\.\./;
	    symlink ($snapFile, $snapAll);
	}
	# also, check old entries to see if they got deleted.
	foreach $entry (keys %oldD) {
	    if (!exists $newD{$entry}) {
		my $fileName = "$init"."$entry"."$ext";
		open (REMOVED, ">>"."$newLoc"."removed");
		print REMOVED "$fileName"."\n";
		close REMOVED;
	    }
	}
    }
    $ftp->quit();

    # get seq file; open new FTP session since PDB sometimes closes it
    $ftp = Net::FTP->new($pdbHostname, Passive=>1);
    $ftp->login($username, $password);
    $ftp->binary();
    $ftp->cwd($pdbIndexRoot);
    my $pdbFile = "pdb_seqres.txt";
    my $myFile = "$newdir/files/seq/pdbseqres.$newdir.fa";
    $ftp->get($pdbFile,$myFile);
    my $newMtime = $ftp->mdtm($pdbFile);
    utime($newMtime, $newMtime, $myFile);
    
    # get index files
    $ftp->cwd("index");
    foreach $pdbFile ($ftp->ls()) {
	$myFile = "$newdir/files/index/$pdbFile";
	$ftp->get($pdbFile,$myFile);
	$newMtime = $ftp->mdtm($pdbFile);
	utime($newMtime, $newMtime, $myFile);
	system("gzip -9 $myFile");
    }
    $ftp->quit();

    die ("Couldn't get (FTP from PDB) $root/data/$newdir/files/seq/pdbseqres.$newdir.fa\n") if (! -e "$newdir/files/seq/pdbseqres.$newdir.fa");

    die ("$root/data/$newdir/files/seq/pdbseqres.$newdir.fa has zero size!  Out of disk space?\n") if (-z "$newdir/files/seq/pdbseqres.$newdir.fa");

    # symlink index, seq dirs to snapshot dir
    symlink ("../files/seq", "$newdir/snapshot/seq");
    symlink ("../files/index", "$newdir/snapshot/index");

    # get updated file of 'on hold' seqs
    $myFile = "$newdir/files/seq/onhold.$newdir.fa";
    my $url = "http://www.rcsb.org/pdb/search/searchStatusDoSearch.do?newSearch=yes&full=true&format=SEQ";
    system("$wget '$url' -O - --quiet > $myFile");

    die ("Couldn't get (HTTP from PDB) $root/data/$newdir/files/seq/onhold.$newdir.fa\n") if (! -e "$newdir/files/seq/onhold.$newdir.fa");

    die ("$root/data/$newdir/files/seq/onhold.$newdir.fa has zero size!  Out of disk space?\n") if (-z "$newdir/files/seq/onhold.$newdir.fa");

    # check for error retrieving files (PDB changed URL)
    open (TMP,"$newdir/files/seq/onhold.$newdir.fa");
    while(<TMP>)
    {
	next if /\s+/;
	last if (/^>/);
	die ("Error retrieving $root/data/$newdir/files/seq/onhold.$newdir.fa  URL changed?\n");
    }
    close TMP;

    # get only protein sequences
    my ($seq, $header, %seq);
    chdir ("$newdir/files/seq");
    $/ = "\n>";
    open (IN, "<pdbseqres.$newdir.fa");
    while (<IN>) {
	($header, $seq) = /^>?([^\n]*)\n(.*)$/s;
	if ($header =~ /\smol\:protein/) { # both protein and protein-het
	    $seq =~ s/[\n>]//sg;
	    $seq{$header} = $seq;
	}
    }
    close (IN);
    open (OUT, ">pdbprot.$newdir.fa");
    foreach $header (sort(keys(%seq))) {
	print(OUT ">$header\n".$seq{$header}."\n");
	delete $seq{$header};
    }
    close (OUT);

    die ("Couldn't create $root/data/$newdir/files/seq/pdbprot.$newdir.fa\n") if (! -e "pdbprot.$newdir.fa");

    die ("$root/data/$newdir/files/seq/pdbprot.$newdir.fa has zero size!  Out of disk space?\n") if (-z "pdbprot.$newdir.fa");

    symlink("pdbprot.$newdir.fa", "pdbprot.fa");
    symlink("pdbseqres.$newdir.fa", "pdbseqres.fa");
    symlink("onhold.$newdir.fa", "onhold.fa");

    # formatdb
    chdir ("$root/blastdb");
    system ("$formatdb -n pdbprot.$newdir -t pdbprot.$newdir -i $root/data/$newdir/files/seq/pdbprot.$newdir.fa -o T");
    system ("cat $root/data/$newdir/files/seq/pdbprot.$newdir.fa $root/data/$newdir/files/seq/onhold.$newdir.fa > $tmpDir/pdbprot+onhold.$newdir.fa");
    system ("$formatdb -n pdbprot+onhold.$newdir -t pdbprot+onhold.$newdir -i $tmpDir/pdbprot+onhold.$newdir.fa -o T");
    unlink("$tmpDir/pdbprot+onhold.$newdir.fa");
    unlink("pdbprot.phr") if (-l "pdbprot.phr");
    unlink("pdbprot.pin") if (-l "pdbprot.pin");
    unlink("pdbprot.psd") if (-l "pdbprot.psd");
    unlink("pdbprot.psi") if (-l "pdbprot.psi");
    unlink("pdbprot.psq") if (-l "pdbprot.psq");
    symlink("pdbprot.$newdir.phr", "pdbprot.phr");
    symlink("pdbprot.$newdir.pin", "pdbprot.pin");
    symlink("pdbprot.$newdir.psd", "pdbprot.psd");
    symlink("pdbprot.$newdir.psi", "pdbprot.psi");
    symlink("pdbprot.$newdir.psq", "pdbprot.psq");
    unlink("pdbprot+onhold.phr") if (-l "pdbprot+onhold.phr");
    unlink("pdbprot+onhold.pin") if (-l "pdbprot+onhold.pin");
    unlink("pdbprot+onhold.psd") if (-l "pdbprot+onhold.psd");
    unlink("pdbprot+onhold.psi") if (-l "pdbprot+onhold.psi");
    unlink("pdbprot+onhold.psq") if (-l "pdbprot+onhold.psq");
    symlink("pdbprot+onhold.$newdir.phr", "pdbprot+onhold.phr");
    symlink("pdbprot+onhold.$newdir.pin", "pdbprot+onhold.pin");
    symlink("pdbprot+onhold.$newdir.psd", "pdbprot+onhold.psd");
    symlink("pdbprot+onhold.$newdir.psi", "pdbprot+onhold.psi");
    symlink("pdbprot+onhold.$newdir.psq", "pdbprot+onhold.psq");

    # make latest point to new dir
    chdir("$root/data");
    if (! $firstRun) {
	unlink("latest") or die ("Couldn't unlink old 'latest' symlink in $root/data\n");
    }
    symlink($newdir, "latest") or die ("Couldn't make new 'latest' symlink in $root/data\n");

    # normal exit
    exit(0);
}

# returns an array of 2*$num_items=14 hashes.
# 1st hash is pdb,
# 2nd is pdb-obs,
# 3rd is cif,
# 4th is cif-obs,
# 5th is xml,
# 6th is xml-obs,
# 7th is bundle.
# Keys are 4-letter pdb code,
# values are mod dates.
# hashes 8-14 are the same, but with noc files.
# there are no bundles with noc files, but the code assumes there might be
sub processLSLR {
    my ($lslr) = @_;

    my @rv;
    my $re; # re to match name of pdb/cif file
    my $re2; # re to match name of noc file

    my $dirType = -1; # which hash current dir entries should go in

    # populate rv
    for ($dirType=0; $dirType<2*$num_items; $dirType++) {
	$rv[$dirType]{"populated"} = 1;
    }

    open (LSLR, $lslr);
    while (<LSLR>) {
	if (/:$/) {
	    # directory name; what's in it?
	    if ((/models/) ||
		(/biounit/) ||
		(/bird/) ||
		(/monomers/) ||
		(/status/) ||
		(/software/) ||
		(/validation_reports/) ||
	        (/extatom/)) {
		$dirType = -1;
	    }
	    elsif ((/bundle-hash/) ||
		   (/pdb_bundle/)) {
		$dirType = 6;
		$re = qr/(....)-pdb-bundle\.tar\.gz$/o;
		$re2 = qr/(....)-noc-bundle\.tar\.gz$/o;
	    }
	    elsif ((/xml-obs-hash/) ||
		   (/data\/structures\/obsolete\/XML/)) {
		$dirType = 5;
		$re = qr/(....)\.xml\.gz$/o;
		$re2 = qr/(....)\.noc\.gz$/o;
	    }
	    elsif ((/xml-hash/) ||
		   (/data\/structures\/divided\/XML/)) {
		$dirType = 4;
		$re = qr/(....)\.xml\.gz$/o;
		$re2 = qr/(....)\.noc\.gz$/o;
	    }
	    elsif ((/cif-obs-hash/) ||
		   (/data\/structures\/obsolete\/mmCIF/)) {
		$dirType = 3;
		$re = qr/(....)\.cif\.gz$/o;
		$re2 = qr/(....)\.noc\.gz$/o;
	    }
	    elsif ((/cif-hash/) ||
		   (/data\/structures\/divided\/mmCIF/)) {
		$dirType = 2;
		$re = qr/(....)\.cif\.gz$/o;
		$re2 = qr/(....)\.noc\.gz$/o;
	    }
	    elsif ((/obs-hash/) ||
		   (/data\/structures\/obsolete\/pdb/)) {
		$dirType = 1;
		$re = qr/pdb(....)\.ent\.gz$/o;
		$re2 = qr/pdb(....)\.noc\.gz$/o;
	    }
	    elsif ((/hash/) ||
		   (/data\/structures\/divided\/pdb/)) {
		$dirType = 0;
		$re = qr/pdb(....)\.ent\.gz$/o;
		$re2 = qr/pdb(....)\.noc\.gz$/o;
	    }
	    else {
		$dirType = -1;
	    }
	}
	if (($dirType > -1) && (($_ =~ $re) || ($_ =~ $re2))) {
	    my $noc = 0;
	    my $key = $1;
	    if ($key !~ /^\d.../) {
		next;
	    }
	    if ($_ =~ $re2) {
		$noc = 1;
	    }
	    $_ =~ /(.{12})\s\S+$/;
	    my $date = $1;
	    # check for recent months... these sometimes have time not year
	    if ($date =~ /:/) {
		my $month1 = $month2num{ lc substr($date, 0, 3) };
		if ($month1 <= $month0) {
		    $date = substr($date, 0, 6)."  "."$year4";
		}
		else {
		    $date = substr($date, 0, 6)."  "."$lastyear4";
		}
	    }

	    if ($noc) {
		$rv[$dirType+$num_items]{$key} = $date;
	    }
	    else {
		$rv[$dirType]{$key} = $date;
	    }
	}
    }
    return \@rv;
}
