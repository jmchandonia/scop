#!/usr/bin/perl
#
#   makelinks.pl - remaking links from a pruned directory.
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
# This program is part of the PAST (PDB Archival Snapshot Toolkit) pacakge.
#
# Contact:
# scope@compbio.berkeley.edu
#
# Description:
#
# This program will remake links from a pruned directory.
# Set $root to the root of the pdb directory.  It takes two
# arguments:  the directory to remake, and the previous snapshot,
# which must not have been pruned.  If the previous snapshot
# was pruned, you have to run this program on it first.

use strict;
use Net::FTP;

# ################## config here ########################

# where is pdb directory on this machine?
my $root = "/lab/db/pdb";

# needs space on temp directory
my $tmpDir = "/tmp";

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
    if ($#ARGV != 1) {
	die ("syntax:  makelinks.pl snapshot previous-snapshot\n");
    }

    my $newdir = $ARGV[0];
    my $olddir = $ARGV[1];

    # what's the current date?
    my @date = localtime(time());
    $month0 = $date[4];  $month0++; # zero indexed, so let's add one
    $year4 = $date[5] + 1900;

    chdir("$root/data/");

    if (! -e "$newdir") {
	die "snapshot '$newdir' does not exist!\n";
    }

    if (! -e "$olddir") {
	die "snapshot '$olddir' does not exist!\n";
    }

    if (! -e "$olddir/snapshot") {
	die "snapshot '$olddir' was pruned; you have to makelinks on it first.\n";
    }

    if (-e "$newdir/snapshot") {
	die "snapshot '$newdir' doesn't seemed to have been pruned.\n";
    }

    if (! -w $newdir) {
	die "you aren't supposed to makelinks on '$newdir'.\n";
    }

    # what are our current files?
    chdir("$olddir/snapshot") or 
	die "Couldn't cd to old snapshot directory!\n";

    # make ls-lR of old directory, following symlinks
    system ("ls -lLR *hash > $tmpDir/tmpOldLSLRm");

    # get dates from this file
    my $aref = processLSLR("$tmpDir/tmpOldLSLRm");
    unlink ("$tmpDir/tmpOldLSLRm");
    my @oldDates = @$aref;

    # make ls-lR of current directory, following symlinks
    chdir("$root/data/");
    chdir("$newdir/files") or 
	die "Couldn't cd to new snapshot directory!\n";
    system ("ls -lLR *hash > $tmpDir/tmpNewLSLRm");

    # get dates from this file
    $aref = processLSLR("$tmpDir/tmpNewLSLRm");
    unlink ("$tmpDir/tmpNewLSLRm");
    my @newDates = @$aref;

    # set up dirs for this to go in
    chdir("$root/data/");
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
     
    # process the data, linking to appropriate files.
    my ($i, $oldSnap, $newLoc, $newSnap, $entry, $init, $ext);
    for ($i=0; $i<2*$num_items; $i++) {
	if (($i % $num_items)==0) {
	    $newLoc = "$newdir/files/hash/";
	    $init = "pdb";
	    $ext = ".ent.gz";
	}
	elsif (($i % $num_items)==1) {
	    $newLoc = "$newdir/files/obs-hash/";
	    $init = "pdb";
	    $ext = ".ent.gz";
	}
	elsif (($i % $num_items)==2) {
	    $newLoc = "$newdir/files/cif-hash/";
	    $init = "";
	    $ext = ".cif.gz";
	}
	elsif (($i % $num_items)==3) {
	    $newLoc = "$newdir/files/cif-obs-hash/";
	    $init = "";
	    $ext = ".cif.gz";
	}
	elsif (($i % $num_items)==4) {
	    $newLoc = "$newdir/files/xml-hash/";
	    $init = "";
	    $ext = ".xml.gz";
	}
	elsif (($i % $num_items)==5) {
	    $newLoc = "$newdir/files/xml-obs-hash/";
	    $init = "";
	    $ext = ".xml.gz";
	}
	else { # ($i % $num_items)==6
	    $newLoc = "$newdir/files/bundle-hash/";
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

	my $href = $oldDates[$i];
	my %oldD = %$href;
	$href = $newDates[$i];
	my %newD = %$href;

	# remove "removed" files from hash
	if (-e "$newLoc/removed") {
	    open (REMOVED, "$newLoc/removed");
	    while (<REMOVED>) {
		if (/$init(....)$ext/) {
		    my $chain = $1;
		    delete $oldD{$chain};
		}
	    }
	    close (REMOVED);
	}

	foreach $entry (keys %oldD, keys %newD) {
	    next if ($entry eq "populated");
	    next if ($entry eq "removed");

	    my $hashDir = substr($entry,1,2)."/";
	    my $fileName = "$init"."$entry"."$ext";
	    my $snapFile = "$newSnap"."$hashDir"."$fileName";

	    next if (-l $snapFile);

	    mkdir("$newSnap"."$hashDir", 0755) if (! -d "$newSnap"."$hashDir");

	    if (exists($newD{$entry})) {
		# link new snapshot to new files
		my $myFile = "$newLoc"."$hashDir"."$fileName";
		symlink ("../../../../".$myFile, $snapFile);
	    }
	    else {
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
    }

    # symlink index, seq dirs to snapshot dir
    if (-e "$newdir/files/seq") {
	symlink ("../files/seq", "$newdir/snapshot/seq");
    }
    if (-e "$newdir/files/index") {
	symlink ("../files/index", "$newdir/snapshot/index");
    }
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
