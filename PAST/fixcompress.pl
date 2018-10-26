#!/usr/bin/perl
#
#   fixcompress.pl - migrate old (pre-1.3) PAST archives to new format
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
# converts .Z to .gz, and comparesses PDB files.
# fixes links in snapshot, if present
# version 9/14/07
#
# Before running, make sure the root variable is set, below.

use strict;

# where is pdb directory on this machine?
my $root = "/lab/db/pdb";

my $year4;

my $debug = 0;

sub processLSLR;

MAIN : {
    chdir("$root/data/");

    # what is dir with old files
    my $oldDir = shift;
    die ("snapshot directory not found\n") if (! -d $oldDir);

    # what is dir with old files
    chdir("$oldDir/files") or 
	die "Couldn't cd to old files directory!\n";

    # make ls-lR of current directory, following symlinks
    system ("ls -lLR *hash > /tmp/tmpOldLSLR1");

    # get dates from this file
    my $aref = processLSLR("/tmp/tmpOldLSLR1");
    unlink("/tmp/tmpOldLSLR1") if (!$debug);
    my @oldDates = @$aref;

    chdir("$root/data");
    
    # process the data
    my ($i, $oldLoc, $newLoc, $newSnap, $entry, $init, $ext);
    for ($i=0; $i<8; $i++) {
	if (($i % 4)==0) {
	    $oldLoc = "$oldDir/files/hash/";
	    $init = "pdb";
	    $ext = ".ent.Z";
	}
	elsif (($i % 4)==1) {
	    $oldLoc = "$oldDir/files/obs-hash/";
	    $init = "pdb";
	    $ext = ".ent.Z";
	}
	elsif (($i % 4)==2) {
	    $oldLoc = "$oldDir/files/cif-hash/";
	    $init = "";
	    $ext = ".cif.Z";
	}
	else {
	    $oldLoc = "$oldDir/files/cif-obs-hash/";
	    $init = "";
	    $ext = ".cif.Z";
	}

	# handle noc files
	if ($i > 3) {
	    $ext =~ s/cif/noc/;
	    $ext =~ s/ent/noc/;
	}

	my $href = $oldDates[$i];
	my %oldD = %$href;
	foreach $entry (keys %oldD) {
	    next if ($entry eq "populated");

	    my $hashDir = substr($entry,1,2)."/";
	    my $oldDate = $oldD{$entry};
	    my $fileName = "$init"."$entry"."$ext";

	    my $oldFile = "$oldLoc"."$hashDir"."$fileName";

	    if ($init eq "pdb") {
		$oldFile =~ s/\.Z//;
	    }

	    next if (! -e $oldFile);

	    print ("converting $oldFile\n");
	    if ($oldFile =~ /\.Z/) {
		if ($debug) {
		    printf ("uncompress $oldFile\n");
		}
		else {
		    system ("uncompress $oldFile");
		}
		$oldFile =~ s/\.Z//;
	    }
	    if ($debug) {
		print ("gzip -9 $oldFile\n");
	    }
	    else {
		system ("gzip -9 $oldFile");
	    }
	}
    }

    # do snapshot if present
    chdir("$root/data/$oldDir/snapshot") or 
	exit(0);

    # make ls-lR of current directory, NOT following symlinks
    system ("ls -lR *hash/ > /tmp/tmpOldLSLR2");

    # get dates from this file
    my $aref = processLSLR("/tmp/tmpOldLSLR2");
    unlink("/tmp/tmpOldLSLR2") if (!$debug);
    my @oldDates = @$aref;

    chdir("$root/data");
    
    # process the data
    my ($i, $oldLoc, $newLoc, $newSnap, $entry, $init, $ext);
    for ($i=0; $i<8; $i++) {
	if (($i % 4)==0) {
	    $oldLoc = "$oldDir/files/hash/";
	    $init = "pdb";
	    $ext = ".ent.Z";
	}
	elsif (($i % 4)==1) {
	    $oldLoc = "$oldDir/files/obs-hash/";
	    $init = "pdb";
	    $ext = ".ent.Z";
	}
	elsif (($i % 4)==2) {
	    $oldLoc = "$oldDir/files/cif-hash/";
	    $init = "";
	    $ext = ".cif.Z";
	}
	else {
	    $oldLoc = "$oldDir/files/cif-obs-hash/";
	    $init = "";
	    $ext = ".cif.Z";
	}

	my $oldSnap = $oldLoc;
	$oldSnap =~ s/files/snapshot/;

	# handle noc files
	if ($i > 3) {
	    $ext =~ s/cif/noc/;
	    $ext =~ s/ent/noc/;
	}

	my $href = $oldDates[$i];
	my %oldD = %$href;
	foreach $entry (keys %oldD) {
	    next if ($entry eq "populated");
	    next if ($entry eq "removed");

	    my $hashDir = substr($entry,1,2)."/";
	    my $fileName = "$init"."$entry"."$ext";
	    my $oldSnapFile = "$oldSnap"."$hashDir"."$fileName";
	    my $newSnapFile = $oldSnapFile;
	    $newSnapFile =~ s/.Z/.gz/;
	    if ($init eq "pdb") {
		$oldSnapFile =~ s/\.Z//;
	    }

	    # link new snapshot to old files
	    my $myNewFile = readlink($oldSnapFile);
	    $myNewFile =~ s/\.Z//;
	    $myNewFile .= ".gz";
	    if ($debug) {
		print(" unlink $oldSnapFile\n");
		print(" symlink ($myNewFile, $newSnapFile)\n");
	    }
	    else {
		unlink($oldSnapFile);
		symlink ($myNewFile, $newSnapFile);
	    }
		
	    # link 'all' directory to hash directory
	    my $snapAll = "$oldSnap"."$fileName";
	    if ($init eq "pdb") {
		$snapAll =~ s/\.Z//;
	    }
	    $snapAll =~ s/hash/all/;
	    $newSnapFile =~ s/$oldDir\/snapshot/\.\./;
	    if ($debug) {
		print(" unlink($snapAll)\n");
	    }
	    else {
		unlink($snapAll);
	    }
	    $snapAll =~ s/.Z/.gz/;
	    if ($init eq "pdb") {
		$snapAll .= ".gz";
	    }
	    if ($debug) {
		print("symlink($newSnapFile, $snapAll)\n");
	    }
	    else {
		symlink($newSnapFile, $snapAll);
	    }
	}
    }
}

# returns an array of 8 hashes.  1st hash is pdb, 2nd is pdb-obs,
# 3rd is cif, 4th is cif-obs.  Keys are 4-letter pdb code,
# values are mod dates.
# hashes 5-8 are the same, but with noc files.
sub processLSLR {
    my ($lslr) = @_;

    my @rv;
    my $re; # re to match name of pdb/cif file
    my $re2; # re to match name of noc file

    my $dirType = -1; # which hash current dir entries should go in

    # populate rv
    for ($dirType=0; $dirType<8; $dirType++) {
	$rv[$dirType]{"populated"} = 1;
    }

    open (LSLR, $lslr);
    while (<LSLR>) {
	if (/:$/) {
	    # directory name; what's in it?
	    if ((/cif-obs-hash/) ||
		(/obsolete\/mmCIF/)) {
		$dirType = 3;
		$re = qr/(....)\.cif\.Z$/o;
		$re2 = qr/(....)\.noc\.Z$/o;
	    }
	    elsif ((/cif-hash/) ||
		     (/divided\/mmCIF/)) {
		$dirType = 2;
		$re = qr/(....)\.cif\.Z$/o;
		$re2 = qr/(....)\.noc\.Z$/o;
	    }
	    elsif (/obs-hash/) {
		$dirType = 1;
		$re = qr/pdb(....)\.ent$/o;
		$re2 = qr/pdb(....)\.noc$/o;
	    }
	    elsif (/obsolete\/pdb/) {
		$dirType = 1;
		$re = qr/pdb(....)\.ent\.Z$/o;
		$re2 = qr/pdb(....)\.noc\.Z$/o;
	    }
	    elsif (/hash/) {
		$dirType = 0;
		$re = qr/pdb(....)\.ent$/o;
		$re2 = qr/pdb(....)\.noc$/o;
	    }
	    elsif (/divided\/pdb/) {
		$dirType = 0;
		$re = qr/pdb(....)\.ent\.Z$/o;
		$re2 = qr/pdb(....)\.noc\.Z$/o;
	    }
	    else {
		$dirType = -1;
	    }
	}
	if (($dirType > -1) && (($_ =~ $re) || ($_ =~ $re2))) {
	    my $noc = 0;
	    my $key = $1;
	    if ($_ =~ $re2) {
		$noc = 1;
	    }
	    $_ =~ /(.{12})\s\S+$/;
	    my $date = $1;
	    # check for current year... these sometimes have time
	    if ($date =~ /:/) {
		$date = substr($date, 0, 6)."  "."$year4";
	    }
	    # printf("%d: %s - %s\n", $dirType, $key, $date);

	    if ($noc) {
		$rv[$dirType+4]{$key} = $date;
	    }
	    else {
		$rv[$dirType]{$key} = $date;
	    }
	}
    }
    return \@rv;
}



