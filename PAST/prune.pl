#!/usr/bin/perl
#
#   prune.pl - pruning back an old PDB snapshot to save on inodes.
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
# Description
#
# This program will prune back an old snapshot of the pdb to
# save on inodes... the "snapshot" directory is deleted, but the
# "files" will still be there.  All info can be reconstructed
# with "makelinks.pl"
#
# Set $root to the root of the pdb directory.

use strict;

# ################## config here ########################

# where is pdb directory on this machine?

my $root = "/lab/db/pdb";

# ################## end of config section ###############

MAIN : {
    if ($#ARGV != 0) {
	die ("syntax:  prune.pl snapshot\n");
    }

    my $newdir = $ARGV[0];

    chdir("$root/data/") or die ("root directory in script does not exist!\n");

    if (! -e "$newdir") {
	die "snapshot '$newdir' does not exist!\n";
    }

    if (! -l "latest") {
	die "'latest' link does not exist!\n";
    }

    if ($newdir eq readlink("latest")) {
	die "you can't prune the latest snapshot.\n";
    }

    if (! -w $newdir) {
	die "you aren't supposed to prune '$newdir'.\n";
    }

    system ("rm -rf $newdir/snapshot");
}
