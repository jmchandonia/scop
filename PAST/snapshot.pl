#!/lab/bin/perl
#
#   snapshot.pl - making a snapshot of the PDB with today's date.
#
#   Copyright (C) 2001-2026 Degui Zhi and John-Marc Chandonia
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
use LWP::UserAgent;
use HTTP::Request;
use HTTP::Date;
use IO::Uncompress::Gunzip qw(gunzip $GunzipError);
use JSON::MaybeXS;
use Data::Dumper;

# ################## config here ########################

# where is pdb snapshot directory on this machine?
my $root = "/lab/db/pdb";

# needs space on temp directory
my $tmpDir = "/tmp";

# where to get pdb files?
my $pdbHostname = "files.wwpdb.org";
my $pdbRoot = "/pub/pdb";

# how many deletions per class of file to tolerate before
# assuming something is wrong?
my $maxDeletions = 999999;

my $formatdb = "/lab/bin/formatdb";
# also needs:  chromium, gzip, ls, cat in your path

# ################## end of config section ###############

my $month0;
my $year4;
my $lastyear4;
our $num_items = 7;

my %month2num = qw(
    Jan 1  Feb 2  Mar 3  Apr 4  May 5  Jun 6
    Jul 7  Aug 8  Sep 9  Oct 10 Nov 11 Dec 12
    );

my %num2month = reverse %month2num;

sub processLSLR;
sub processJSON;
sub dateKey;

MAIN : {
    # check that executables exist
    die("Can't find formatdb\n") if (! -x "$formatdb");

    # make sure tmp exists
    die("tmpDir not configured to a writeable directory\n") if 
	(! -w "$tmpDir") || (! -d "$tmpDir");

    # check that the pdb snapshot directory is OK
    my $firstRun = 0;
    if (! -l "$root/data/latest") {
        if (-d "$root/data/latest") {
	    die("$root/data/latest must be a symlink, not a directory.\nIf you're running this for the first time, please delete the directory and\nrun this program again.\n");
        }

        my $question = "Can't find pdb snapshot directory $root/data/latest
\t Create a new snapshot directory? (c)
\t or Abort (and modify the configuration in snapshot.pl)? (a)\n";
        my $answer = "";
        while (($answer ne "C") and ($answer ne "A")) {
            print $question;
            my $line = <STDIN>;
            $answer = uc(substr($line, 0, 1));
        }
        die("\n") if ($answer eq "A");

        # create new snapshot directory
        mkdir("$root", 0755) if not -e $root;
        mkdir("$root/data", 0755) if not -e "$root/data";
        mkdir("$root/blastdb", 0755) if not -e "$root/blastdb";
        chdir("$root");
        symlink("data/latest/snapshot/seq", "seq") or die("Couldn't make symlink in $root\n");
        symlink("data/latest/snapshot/all", "all") or die("Couldn't make symlink in $root\n");
        symlink("data/latest/snapshot/hash", "hash") or die("Couldn't make symlink in $root\n");
        symlink("data/latest/snapshot/obs-all", "obs-all") or die("Couldn't make symlink in $root\n");
        symlink("data/latest/snapshot/obs-hash", "obs-hash") or die("Couldn't make symlink in $root\n");
        symlink("data/latest/snapshot/cif-all", "cif-all") or die("Couldn't make symlink in $root\n");
        symlink("data/latest/snapshot/cif-hash", "cif-hash") or die("Couldn't make symlink in $root\n");
        symlink("data/latest/snapshot/cif-obs-all", "cif-obs-all") or die("Couldn't make symlink in $root\n");
        symlink("data/latest/snapshot/cif-obs-hash", "cif-obs-hash") or die("Couldn't make symlink in $root\n");
        symlink("data/latest/snapshot/xml-all", "xml-all") or die("Couldn't make symlink in $root\n");
        symlink("data/latest/snapshot/xml-hash", "xml-hash") or die("Couldn't make symlink in $root\n");
        symlink("data/latest/snapshot/xml-obs-all", "xml-obs-all") or die("Couldn't make symlink in $root\n");
        symlink("data/latest/snapshot/xml-obs-hash", "xml-obs-hash") or die("Couldn't make symlink in $root\n");
        symlink("data/latest/snapshot/bundle-all", "bundle-all") or die("Couldn't make symlink in $root\n");
        symlink("data/latest/snapshot/bundle-hash", "bundle-hash") or die("Couldn't make symlink in $root\n");
        chdir("$root/data");

        $firstRun = 1;
    }

    # upgrade from older versions not supporting bundles
    if (! -l "$root/bundle-all") {
        chdir("$root");
        symlink("data/latest/snapshot/bundle-all", "bundle-all") or die("Couldn't make symlink in $root\n");
        symlink("data/latest/snapshot/bundle-hash", "bundle-hash") or die("Couldn't make symlink in $root\n");
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
        die("Directory for today's date already exists; remove first!\n");
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
            die("Couldn't cd to latest snapshot directory: $olddir\n");

        # make ls-lR of current directory, following symlinks
        system("ls -lLR *hash > $tmpDir/tmpOldLSLR");

        # get dates from this file
        $aref = processLSLR("$tmpDir/tmpOldLSLR");
        print Dumper($aref);
        # unlink("$tmpDir/tmpOldLSLR");
        @oldDates = @$aref;
    }

    # Initialize LWP User Agent
    my $ua = LWP::UserAgent->new(
        timeout => 600,
	);

    # new code to deal with PDB's json indices
    my $pdbUrl = "https://$pdbHostname";
    my $pdbRootUrl = "$pdbUrl$pdbRoot";

    # Get holdings files
    my $holdingsFile = "$tmpDir/tmpNewHoldings.json.gz";
    my $obsHoldingsFile = "$tmpDir/tmpNewHoldings2.json.gz";
    my $modDatesFile = "$tmpDir/tmpNewDates1.json.gz";
    my $obsModDatesFile = "$tmpDir/tmpNewDates2.json.gz";

    # Download current_file_holdings.json.gz
    my $response = $ua->get("$pdbRootUrl/holdings/current_file_holdings.json.gz", 
                            ':content_file' => $holdingsFile);
    die("Failed to download $pdbRootUrl/holdings/current_file_holdings.json.gz: ", $response->status_line)
        unless $response->is_success;

    # Download all_removed_entries.json.gz
    $response = $ua->get("$pdbRootUrl/holdings/all_removed_entries.json.gz", 
			 ':content_file' => $obsHoldingsFile);
    die("Failed to download all_removed_entries.json.gz: ", $response->status_line)
        unless $response->is_success;

    # Download released_structures_last_modified_dates.json.gz
    $response = $ua->get("$pdbRootUrl/holdings/released_structures_last_modified_dates.json.gz", 
			 ':content_file' => $modDatesFile);
    die("Failed to download released_structures_last_modified_dates.json.gz: ", $response->status_line)
        unless $response->is_success;

    # Download obsolete_structures_last_modified_dates.json.gz
    $response = $ua->get("$pdbRootUrl/holdings/obsolete_structures_last_modified_dates.json.gz", 
			 ':content_file' => $obsModDatesFile);
    die("Failed to download obsolete_structures_last_modified_dates.json.gz: ", $response->status_line)
        unless $response->is_success;

    $aref = processJSON($holdingsFile, $obsHoldingsFile, $modDatesFile, $obsModDatesFile);
    my @newDates = @$aref;

    print Dumper($aref);

    # clean up
    # unlink("$tmpDir/tmpNewHoldings.json.gz");
    # unlink("$tmpDir/tmpNewHoldings2.json.gz");
    # unlink("$tmpDir/tmpNewDates1.json.gz");
    # unlink("$tmpDir/tmpNewDates2.json.gz");

    # set up dirs for this to go in
    chdir("$root/data");
    mkdir("$newdir", 0755) or 
        die("Couldn't make new data directory $newdir");
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
            next if ($entry eq "nEntries");

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
            printf("debug:  entry is $entry\n");
            printf("debug:  oldDate is $oldDate\n");
            printf("debug:  newDate is $newDate\n");
            if ($oldDate ne $newDate) {
                my $oldDateKey = dateKey($oldDate);
                my $newDateKey = dateKey($newDate);
                if ((defined $oldDateKey) && (defined $newDateKey) && ($newDateKey le $oldDateKey)) {
                    printf("debug:  newDate is not newer than oldDate; reusing local file\n");
                }
                else {
                    # check actual mod times
                    my $pdbFile = "$pdbLoc"."$hashDir"."$fileName";
                    if ($i == 6) {
                        $pdbFile = "$pdbLoc"."$hashDir"."$entry/"."$fileName";
                    }

                    my $myFile = "$newLoc"."$hashDir"."$fileName";

                    # Get modification time via HEAD request
                    my $newMtime;
                    my $pdbFileUrl = "$pdbRootUrl/$pdbFile";
                    my $request = HTTP::Request->new('HEAD', $pdbFileUrl);
                    my $response = $ua->request($request);
                    if ($response->is_success) {
                        my $last_modified = $response->header('Last-Modified');
                        if ($last_modified) {
                            $newMtime = str2time($last_modified);
                            printf("debug:  modtime at PDB is $newMtime\n");
                        } else {
                            die "Could not retrieve Last-Modified header";
                        }
                    }

                    while (!$newMtime) {
                        if ($resetTime > 10000) {
                            die "Couldn't connect to PDB after multiple tries: ",$response->status_line;
                        }
                        # re-open connection
                        sleep($resetTime);
                        $resetTime *= 1.5;
                        print("Retrying connection to $pdbFileUrl\n");
                        system("date");
                        $response = $ua->request($request);
                        if ($response->is_success) {
                            my $last_modified = $response->header('Last-Modified');
                            $newMtime = str2time($last_modified);
                        }
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
                    printf("debug:  local modtime is $oldMtime\n");
                    if ($oldMtime != $newMtime) {
                        # download the file to new files
                        mkdir("$newLoc"."$hashDir", 0755) if (! -d "$newLoc"."$hashDir");
                        system("date");
                        printf("getting file $pdbFile\n");
                        $response = $ua->get($pdbFileUrl,
					     ':content_file' => $myFile);
                        printf("debug:  getting file\n");
                        printf("debug:  pdbFile is $pdbFile\n");
                        printf("debug:  myFile is $myFile\n");
                        if ($response->is_success) {
                            utime($newMtime, $newMtime, $myFile);    
                            $downloaded = 1;
                        }

		        while (!$downloaded) {
			    if ($resetTime > 10000) {
			        die "Couldn't download file multiple tries: ",$response->status_line;
			    }
			    # re-open connection
			    sleep($resetTime);
			    $resetTime *= 1.5;
			    print("Retrying connection to $pdbFileUrl\n");
			    system("date");
			    $response = $ua->get("$pdbFileUrl",
						 ':content_file' => $myFile);
			    if ($response->is_success) {
			        utime($newMtime, $newMtime, $myFile);    
			        $downloaded = 1;
			    }
		        }
		    }
                }
	    }

            if ($downloaded) {
                # link new snapshot to newly downloaded file
                my $myFile = "$newLoc"."$hashDir"."$fileName";
                symlink("../../../../".$myFile, $snapFile);
            }
            else {
                # link new snapshot to old files
                my $myOldSnap = "$oldSnap"."$hashDir"."$fileName";
                my $myOldFile = readlink($myOldSnap);
                symlink($myOldFile, $snapFile);
            }
            
            # link 'all' directory to hash directory
            my $snapAll = "$newSnap"."$fileName";
            $snapAll =~ s/hash/all/;
            $snapFile =~ s/$newdir\/snapshot/\.\./;
            symlink($snapFile, $snapAll);
        }
        # also, check old entries to see if they got deleted.
        my $nDeleted = 0;
        foreach $entry (keys %oldD) {
            if (!exists $newD{$entry}) {
                if ($nDeleted++ > $maxDeletions) {
                    die("Too many files deleted; probably a bug in PDB indices");
                }
                my $fileName = "$init"."$entry"."$ext";
                open(REMOVED, ">>"."$newLoc"."removed");
                print REMOVED "$fileName"."\n";
                close(REMOVED);
            }
        }
    }

    # get seq file; open new HTTP session
    my $pdbFileUrl = "$pdbRootUrl/derived_data/pdb_seqres.txt";
    my $myFile = "$newdir/files/seq/pdbseqres.$newdir.fa";
    $response = $ua->get($pdbFileUrl, ':content_file' => $myFile);
    if ($response->is_success) {
        # Get modification time
        my $request = HTTP::Request->new('HEAD', $pdbFileUrl);
        my $mtime_response = $ua->request($request);
        if ($mtime_response->is_success) {
            my $last_modified = $mtime_response->header('Last-Modified');
            my $newMtime = str2time($last_modified);
            utime($newMtime, $newMtime, $myFile);
        } else {
            die "Failed to retrieve modification time for seq file: ", $mtime_response->status_line;
        }
    } else {
        die("Couldn't get (HTTPS from PDB) $root/data/$newdir/files/seq/pdbseqres.$newdir.fa\n");
    }
    
    # get index files
    my $indexHtml = `chromium  --headless --disable-gpu --virtual-time-budget=5000 --dump-dom https://files.wwpdb.org/pub/pdb/derived_data/index/`;
    my @indexFiles = ($indexHtml =~ /<a href="([^"]+)"/g);
    foreach my $fileUrl (@indexFiles) {
	next if $fileUrl =~ /parent/; # Skip parent directories
	next if $fileUrl !~ /$pdbHostname/; # Skip external links
	my $pdbFile = $fileUrl;
	$pdbFile =~ s/.*\///;;
	my $myFile = "$newdir/files/index/$pdbFile";
	$response = $ua->get($fileUrl, ':content_file' => $myFile);
	if ($response->is_success) {
	    # Get modification time
	    my $request = HTTP::Request->new('HEAD', $fileUrl);
	    my $mtime_response = $ua->request($request);
	    if ($mtime_response->is_success) {
		my $last_modified = $mtime_response->header('Last-Modified');
		my $newMtime = str2time($last_modified);
		utime($newMtime, $newMtime, $myFile);
	    } else {
		die "Failed to retrieve modification time for index file $pdbFile: ", $mtime_response->status_line;
	    }
	    system("gzip -9 $myFile");
	} else {
	    die "Failed to download index file $pdbFile: ", $response->status_line;
	}
    }

    die("Couldn't get (HTTPS from PDB) $root/data/$newdir/files/seq/pdbseqres.$newdir.fa\n") if (! -e "$newdir/files/seq/pdbseqres.$newdir.fa");

    die("$root/data/$newdir/files/seq/pdbseqres.$newdir.fa has zero size!  Out of disk space?\n") if (-z "$newdir/files/seq/pdbseqres.$newdir.fa");

    # symlink index, seq dirs to snapshot dir
    symlink("../files/seq", "$newdir/snapshot/seq");
    symlink("../files/index", "$newdir/snapshot/index");

    # get updated file of 'on hold' seqs
    my $myFile = "$newdir/files/seq/onhold.$newdir.fa";
    my $url = "https://www.rcsb.org/search/fastaDownload?pdbid=&authorList=&title=&status=&sequenceAvailable=&date=&excludeWDRN=yes&displayType=fasta";
    $response = $ua->get($url, ':content_file' => $myFile);
    if ($response->is_success) {
        # Ensure proper modification time (assuming server provides Last-Modified)
        my $request = HTTP::Request->new('HEAD', $url);
        my $mtime_response = $ua->request($request);
        if ($mtime_response->is_success) {
            my $last_modified = $mtime_response->header('Last-Modified');
            my $newMtime = str2time($last_modified);
            utime($newMtime, $newMtime, $myFile);
        } else {
            die "Failed to retrieve modification time for onhold file: ", $mtime_response->status_line;
        }
    } else {
        die("Couldn't get (HTTPS from PDB) $root/data/$newdir/files/seq/onhold.$newdir.fa\n");
    }

    die("$root/data/$newdir/files/seq/onhold.$newdir.fa has zero size!  Out of disk space?\n") if (-z "$newdir/files/seq/onhold.$newdir.fa");

    # check for error retrieving files (PDB changed URL)
    open(TMP,"$newdir/files/seq/onhold.$newdir.fa");
    while(<TMP>) {
        next if /\s+/;
        last if (/^>/);
        die("Error retrieving $root/data/$newdir/files/seq/onhold.$newdir.fa  URL changed?\n");
    }
    close(TMP);

    # get only protein sequences
    my ($seq, $header, %seq);
    chdir("$newdir/files/seq");
    $/ = "\n>";
    open(IN, "<pdbseqres.$newdir.fa");
    while (<IN>) {
        ($header, $seq) = /^>?([^\n]*)\n(.*)$/s;
        if ($header =~ /\smol\:protein/) { # both protein and protein-het
            $seq =~ s/[\n>]//sg;
            $seq{$header} = $seq;
        }
    }
    close(IN);
    open(OUT, ">pdbprot.$newdir.fa");
    foreach $header (sort(keys(%seq))) {
        print(OUT ">$header\n".$seq{$header}."\n");
        delete $seq{$header};
    }
    close(OUT);

    die("Couldn't create $root/data/$newdir/files/seq/pdbprot.$newdir.fa\n") if (! -e "pdbprot.$newdir.fa");

    die("$root/data/$newdir/files/seq/pdbprot.$newdir.fa has zero size!  Out of disk space?\n") if (-z "pdbprot.$newdir.fa");

    symlink("pdbprot.$newdir.fa", "pdbprot.fa");
    symlink("pdbseqres.$newdir.fa", "pdbseqres.fa");
    symlink("onhold.$newdir.fa", "onhold.fa");

    # formatdb
    chdir("$root/blastdb");
    system("$formatdb -n pdbprot.$newdir -t pdbprot.$newdir -i $root/data/$newdir/files/seq/pdbprot.$newdir.fa -o T");
    system("cat $root/data/$newdir/files/seq/pdbprot.$newdir.fa $root/data/$newdir/files/seq/onhold.$newdir.fa > $tmpDir/pdbprot+onhold.$newdir.fa");
    system("$formatdb -n pdbprot+onhold.$newdir -t pdbprot+onhold.$newdir -i $tmpDir/pdbprot+onhold.$newdir.fa -o T");
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
        unlink("latest") or die("Couldn't unlink old 'latest' symlink in $root/data\n");
    }
    symlink($newdir, "latest") or die("Couldn't make new 'latest' symlink in $root/data\n");

    # normal exit
    exit(0);
}

sub dateKey {
    my ($date) = @_;
    return undef if (! defined $date);
    return undef if ($date !~ /^([A-Z][a-z][a-z])\s+(\d{1,2})\s+(\d{4})$/);

    my $month = $month2num{$1};
    return undef if (! defined $month);

    return sprintf("%04d%02d%02d", $3, $month, $2);
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
        $rv[$dirType]{"nEntries"} = 0;
    }

    open(LSLR, $lslr);
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
                   (/obsolete\/XML/)) {
                $dirType = 5;
                $re = qr/(....)\.xml\.gz$/o;
                $re2 = qr/(....)\.noc\.gz$/o;
            }
            elsif ((/xml-hash/) ||
                   (/divided\/XML/)) {
                $dirType = 4;
                $re = qr/(....)\.xml\.gz$/o;
                $re2 = qr/(....)\.noc\.gz$/o;
            }
            elsif ((/cif-obs-hash/) ||
                   (/obsolete\/mmCIF/)) {
                $dirType = 3;
                $re = qr/(....)\.cif\.gz$/o;
                $re2 = qr/(....)\.noc\.gz$/o;
            }
            elsif ((/cif-hash/) ||
                   (/divided\/mmCIF/)) {
                $dirType = 2;
                $re = qr/(....)\.cif\.gz$/o;
                $re2 = qr/(....)\.noc\.gz$/o;
            }
            elsif ((/obs-hash/) ||
                   (/obsolete\/pdb/)) {
                $dirType = 1;
                $re = qr/pdb(....)\.ent\.gz$/o;
                $re2 = qr/pdb(....)\.noc\.gz$/o;
            }
            elsif ((/hash/) ||
                   (/divided\/pdb/)) {
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
                my $month1 = $month2num{ substr($date, 0, 3) };
                if ($month1 <= $month0) {
                    $date = substr($date, 0, 6)."  "."$year4";
                }
                else {
                    $date = substr($date, 0, 6)."  "."$lastyear4";
                }
            }

            if ($noc) {
                $rv[$dirType+$num_items]{$key} = $date;
                $rv[$dirType+$num_items]{"nEntries"}++;
            }
            else {
                $rv[$dirType]{$key} = $date;
                $rv[$dirType]{"nEntries"}++;
            }
        }
    }
    return \@rv;
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
# there appear to be no more noc files, but these are kept as legacy code
sub processJSON {
    my ($holdings, $obsHoldings, $modDates, $obsModDates) = @_;
    my @rv;

    # populate rv
    for (my $dirType=0; $dirType<2*$num_items; $dirType++) {
        $rv[$dirType]{"nEntries"} = 0;
    }

    # load in dates
    for (my $i=0; $i<2; $i++) {
        my $file = $modDates;
        my $holdingsFile = $holdings;
        if ($i==1) {
            $file = $obsModDates;
            $holdingsFile = $obsHoldings;
        }

        my $json_string;
        gunzip $holdingsFile => \$json_string
            or die "Failed gunzipping $holdingsFile: $GunzipError\n";
        my $data = decode_json $json_string;

        gunzip $file => \$json_string
            or die "Failed gunzipping $file: $GunzipError\n";
        my $dates = decode_json $json_string;
        foreach my $code (keys %$dates) {
            my $date = $dates->{$code};
            # hack because PDB's dates don't seem to parse consistently
            if ($date =~ /^(\d\d\d\d)-(\d\d)-(\d\d)/) {
                my $y = $1;
                my $m = $2;
                my $d = $3;
                $m =~ s/^0//;
                $d =~ s/^0/ /;
                $m = $num2month{$m};
                $date = "$m $d  $y";
            }
            if ((exists $data->{$code}->{'pdb'}) ||
                (exists $data->{$code}->{'content_type'}->{'pdb'})) {
                $rv[$i]{lc $code} = $date;
                $rv[$i]{"nEntries"}++;
            }
            if ((exists $data->{$code}->{'mmcif'}) ||
                (exists $data->{$code}->{'content_type'}->{'mmcif'})) {
                $rv[$i+2]{lc $code} = $date;
                $rv[$i+2]{"nEntries"}++;
            }
            if ((exists $data->{$code}->{'pdbml'}) ||
                (exists $data->{$code}->{'content_type'}->{'pdbml'})) {
                $rv[$i+4]{lc $code} = $date;
                $rv[$i+4]{"nEntries"}++;
            }
            if (exists $data->{$code}->{'pdb_bundle'}) {
                $rv[$i+6]{lc $code} = $date;
                $rv[$i+6]{"nEntries"}++;
                die ("need support for obsolete bundles") if ($i==1);
            }
        }
    }

    return \@rv;
}
