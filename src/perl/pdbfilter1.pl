#!/lab/bin/perl -w
#
#    Copyright (c) 2003-2018, The Regents of the University of California
#
#    This program is free software: you can redistribute it and/or
#    modify it under the terms of the GNU Lesser General Public License
#    as published by the Free Software Foundation, either version 3 of
#    the License, or (at your option) any later version.
#
#    This program is distributed in the hope that it will be useful,
#    but WITHOUT ANY WARRANTY; without even the implied warranty of
#    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
#    Lesser General Public License for more details.
#
#    You should have received a copy of the GNU Lesser General Public
#    License along with this program.  If not, see
#    <http://www.gnu.org/licenses/>.

# changelog:
# 7/22/16 - fixed infinite loop bug (JMC)
# 4/27/16 - fixed regexp for residue ranges (JMC)
# 10/31/13 - changed DB name to SCOPe (JMC)
# 1/4/12 - add ASTEROIDS features (JMC)
# 12/21/11 - makes a single pdb-style file (JMC)
# 12/4/07 - fixed pdb location, gzip
# 04/18/2006 - debug building v1.69d (DZ)
# 2/7/05 - fixed bug in 6-digit sunid
# 11/21/03 - added -8 option (JMC)
# 4/18/03 - changed REMARK headers (JMC), made opt_a and opt_b optional
# 4/17/03 - added -a, -b, -v options (JMC)

=head1 NAME

 pdbfilter1.pl - Creates PDB-style file for a single SCOPe domain or ASTEROID

=head1 SYNOPSIS

  perl pdbfilter1 -p <gzipped pdb file> -o <output file> -r <RAF file>
                  -i <RAF index> -s <SCOPe sid> -d <SCOPe node description>
                  -u <SCOPe sunid> -c <SCOPe sccs>
                  -a <comments> -b <SPACI line> -v <version>
                  -P <Pfam_version> -A <ASTEROIDS header>

=head1 DESCRIPTION

  All arguments are mandatory except -a, and -b. The following is a
  brief description of the arguments:

    -p : PDB file, gzipped

    -o : output PDB-style file

    -r : The RAF map file, e.g., astral-rapid-access-1.57.raf.  There
         must be one line in the file per chain in the domain

    -i : RAF index file; doesn't have to exist

    -s : SCOPe sid, e.g., d1dlwa_

    -d : SCOPe node description, e.g., 1pys B:1-38,B:152-190

    -u : SCOPe sunid

    -c : SCOPe sccs

    -a : Alexey's comments on PDB files

    -b : line of SPACI scores, same format as SPACI text file

    -v : version number of ASTRAL, i.e. 1.57

    -P : Pfam version, i.e., 23.0 (optional)

    -A : ASTEROIDS header (optional)

=cut

use RAF;
use Getopt::Std;

my $opts;
$opts =
    $opt_p || # /usr/local/db/pdb/data/010331/snapshot/hash/jm/pdb1jmc.ent.gz
    $opt_o || # d1dlwa_.ent
    $opt_r || # /tmp/1dlw.raf
    $opt_i || # /tmp/1dlw.idx
    $opt_s || # d1dlwa_
    $opt_d || # 1pys B:1-38,B:152-190
    $opt_u || # 25032
    $opt_c || # b.40.2.1
    $opt_v || # version, i.e. 1.57
    $opt_a || # string w/ lines(s) from comments file
    $opt_b || # string w/ line of SPACI scores
    $opt_P ||
    $opt_A ||
    $opts;

my @months = ("JAN","FEB","MAR","APR","MAY","JUN",
	      "JUL","AUG","SEP","OCT","NOV","DEC");

my @PDBfile = ();

MAIN:
{
    # get arguments
    init(1);

    my $version = $opt_v;

    # initialize RAF and SCOP objects
    my $count = 0;
    my $raf = RAF::new($opt_r,$opt_i);

    # list of "bad words" from Alexey's comments
    # these should be calculated elsewhere and already in the db.
    my %badlist = qw (chimer 1 circular 1 disorder 1
		      error 1 incorrect 1 interrupted 1
		      misfolded 1 missing 1
		      mistraced 1 mutant 1 permut 1
		      truncat 1);

    # copy atom and hetatom records
    my $pdbHash;

    my $filename;

    # find date
    open (DATE, "date|");
    my @makedate = split(/\s+/,<DATE>);
    $makedate[2] = "0".$makedate[2] unless length($makedate[2])==2;
    my $makedate = $makedate[2] . "-" . (uc $makedate[1]) . "-" . (substr($makedate[5],2,2));
    close(DATE);

    my @entries;
    my ($revdat, $headdate);
    my ($code, $entry, $sun, $sccs);
    my ($segment, $segCount);
    my (@atm,@res);
    my ($chainID, $range);
    my ($PDBChainID,$chain);
    my ($date, $day,$mon,$year);
    my $lastline;
    my (@resdata, @lines);
    my ($j,$linecount);
    my $file;

    $entry = $opt_p;
    $sid = $opt_s;
    $code = substr($sid,1,4);
    $sid = $opt_s;
    $sun = $opt_u;
    $sccs = $opt_c;

    my $prev_file = "";
    my ($model, $firstmodelnum);

    # open output file
    $filename = $opt_o;
	    
    # read from PDB file, create hash
    $model=0;  # is NMR model
    $firstmodelnum=" ";

    open(PDBFile,"gzip -dc $opt_p|");
    ($revdat, $headdate,$model, $firstmodelnum) = getFileContents();   # stuffs data into @PDBfile
    close(PDBFile);

    # segments delimited by commas (ie "l:,h:1-7,h:32-41" for "1bi6")
    $segCount = 0;
    my $chainInfo = substr($opt_d,5);
    my @segments = split(/,/,$chainInfo);

    my $maxoffset=0;  # for next round
    my $offset;
    my $modelnum;
    my $started=0;  # no lines added to outfile

    while (1) {
	my $currseg=0;
	my $minoffset=$maxoffset;  # for this round

	foreach $segment (@segments) {
	    $currseg++;

	    # get chainID and range
	    ($chainID, $range) = ($segment =~ /(\S:)?(\S+?-\S+)?$/);
	    
	    if (!defined $chainID) {
		$chainID = "_";
	    } else {
		$chainID = uc (substr ($chainID,0,1));   # uppercase is common case
	    }
	    
	    if (!defined $range) {
		$range = "-";
	    }
	    
	    # lookup chain in RAF
	    $PDBChainID = $code . $chainID;
	    $chain = $raf->getChain($PDBChainID);

	    # try with uppercase chainID
	    if (!defined $chain) {
		$chainID = lc $chainID;
		
		# lookup chain in RAF
		$PDBChainID = $code . $chainID;
		$chain = $raf->getChain($PDBChainID);
	    }
	    
	    if (!defined $chain) {
		print ("$PDBChainID not defined, skipping\n");
		next;
	    }
	    
	    # new chain found, so make the file and print header info
	    if (!$segCount++) {
		open(OUTFILE,">$filename");
		
		if (defined $revdat) {
		    $date = $revdat;
		} elsif (defined $headdate) {
		    $date = $headdate;
		} else {
		    (undef,undef,undef,$day,$mon,$year)=localtime((stat($entry))[9]);
		    $date = $day. "-" . $months[$mon] . "-" . (($year > 100)?("0" . ($year-100)):$year);
		}

		# look up spaci/aerospaci scores
		my (@spaci, $aerospaci, $spacicomment, $aerocomment);
		if ((defined $opt_b) && (defined $opt_a)) {
		    my $spaci = $opt_b;
		    @spaci = split (' ', $spaci);
		    my $comment = $opt_a;
		    $comment = "" if (! defined $comment);
		    $aerospaci = $spaci[0];
		    $aerocomment = "";
		    my $key;
		    foreach $key (keys %badlist) {
			if ($comment =~ /$key/i) {
			    $aerospaci -= 2.0;
			    $aerocomment = " ($key)";
			    last;
			}
		    }
		    if ($spaci[1] =~ /THEORY/) {
			$aerospaci -= 5.0;
			$aerocomment = " (theory)";
		    }
		    my $valid = 1;
		    if ($spaci[1] !~ /-|(XRAY)/) {
			$valid = 0;
		    } else {
			if ($spaci[6] =~ /NA/) {
			    $valid = 0;
			}
			if ($spaci[7] =~ /-|(NA)/) {
			    $valid = 0;
			}
		    }
		    if (grep {/NA/} @spaci[12..14]) {
			$valid = 0;
		    }
		    if (grep {/NA/} @spaci[8..11]) {
			$valid = 0;
		    }
		    $spacicomment = "";
		    if ($valid==0) {
			$spacicomment = " (not valid)";
		    }
		}

		# print out HEADER/REMARK info
		printf OUTFILE ("HEADER    SCOPe/ASTRAL domain %s %8.8s    %s   0000\n",
				$sid, "[".$sun."]", $makedate);
		printf OUTFILE ("REMARK  99\n");
		if (defined $opt_P) {
		    printf OUTFILE ("REMARK  99 ASTRAL ASTEROIDS\n");
		}
		printf OUTFILE ("REMARK  99 ASTRAL ASTRAL-version: %s\n", $version);
		if (defined $opt_P) {
		    printf OUTFILE ("REMARK  99 ASTRAL Pfam-version: %s\n", $opt_P);
		    printf OUTFILE ("REMARK  99 ASTRAL ASTEROIDS-sid: %s\n", $sid);
		    printf OUTFILE ("REMARK  99 ASTRAL SCOPe-sun: UNDEFINED\n");
		}
		else {
		    printf OUTFILE ("REMARK  99 ASTRAL SCOPe-sid: %s\n", $sid);
		    printf OUTFILE ("REMARK  99 ASTRAL SCOPe-sun: %s\n", $sun);
		}
		printf OUTFILE ("REMARK  99 ASTRAL SCOPe-sccs: %s\n", $sccs);
		printf OUTFILE ("REMARK  99 ASTRAL Source-PDB: %s\n", $code);
		printf OUTFILE ("REMARK  99 ASTRAL Source-PDB-REVDAT: %s\n", $date);
		printf OUTFILE ("REMARK  99 ASTRAL Region: %s\n", $chainInfo);
		if (defined $opt_A) {
		    printf OUTFILE ("REMARK  99 ASTRAL ASTEROIDS-evidence: %s\n", $opt_A);
		}
		if ((defined $opt_b) && (defined $opt_a)) {
		    printf OUTFILE ("REMARK  99 ASTRAL ASTRAL-SPACI: %s%s\n", $spaci[0],$spacicomment);
		    printf OUTFILE ("REMARK  99 ASTRAL ASTRAL-AEROSPACI: %s%s\n", $aerospaci,$aerocomment);
		}
		printf OUTFILE ("REMARK  99 ASTRAL Data-updated-release: %s\n",$version);
	    }
	    
	    # get resID sequence in range
	    my $resids = $chain->getMapAbs($range,2);
	    if (defined $resids) {
		@res = split(/ +/, $chain->getMapAbs($range,2));
	    }
	    else {
		print ("\nERROR - dom=".$sid);
		print ("\nERROR - range=$range");
		print ("\nERROR - chain=".$chain->getName());
	    }

	    # format $chainID correctly, assume case of chainID is same in RAF as in PDB
	    if ($chainID eq "_") {
		$chainID = " ";
	    }
	    
	    my $resIndex = 0;
	    $offset=0;
	    
	    # advance @PDBfile to correct chain
	    while (1) {
		# advance offset with respect to previous

		# Gary 11/21
		($offset,$modelnum) = advanceToChain($chainID,
						     (($minoffset > $offset) ? $minoffset : $offset),
						     $res[0], $model);  #returns absolute line number

		# stop loop when end of file, or match ATOM
		if (($offset == -1) || ($PDBfile[$offset] =~ /^ATOM|^HETATM/)) {
		    last;
		}
	    }

	    # first model special, use $firstmodelnum;
	    if ($segCount==1) {
		$modelnum = $firstmodelnum;
	    }

	    # no repeat
	    if ($offset == -1) {
		next;
	    }

	    my $currline=0;		    
	    my $found = 0;
	    my @datasofar = ();
	    my $datacount=0;
	    my $rescode;

	    my $nextgoodline = $offset;   # Gary added 11/11

	    # build mini-PDB file
LINELOOP:
	    foreach (@PDBfile) {
		# skip until get to line with chain/res ID; skip all ACE
	        # if (($offset > $currline) || ($_ =~ /^HETATM.+ACE/)) {        # commented out, Gary 11/11
		if (($nextgoodline > $currline) || ($_ =~ /^HETATM.+ACE/)) {  # Gary added 11/11
		    $currline++;
		    next;
		}

		if (($_ =~ /^ENDMDL/) &&
		    (!($resIndex == (scalar @res - 1)) && ($found))) {
		    if ($maxoffset < $currline) {
			$maxoffset = $currline;
		    }
		    last;
		}

		if ($maxoffset < $currline) {
		    $maxoffset = $currline;
		}

		# only check ATOM, HETATM, TER records
		if ($_ =~ /^ATOM|^HETATM|^TER|^END/) {
		    if (inPDBfile($chainID,$res[$resIndex], $_)) {
			my ($newrescode) = ($_ =~ /^.{6}\s*\S+.{6}\s*(\S+) /);

			$nextgoodline = $currline + 1;  # Gary added 11/11

			# fix problem of res code repeats
			if ($found) {

			    # if codes are different
			    if ($newrescode ne $rescode) {

				# if next resIndex is same as current, include it
				if ((defined $res[$resIndex+1]) &&
				    ($res[$resIndex+1] eq $res[$resIndex])) {
				    $resIndex++;
				    
				} 
				# otherwise, don't include it
				else {
				    $currline++;  # Gary added 4/16/03
				    next;
				}
			    }
			}

			$found = 1;
			$rescode = $newrescode;

			# don't put in TER lines, add later
			if (!($_ =~ /^TER/)) {
			    $datasofar[$datacount++] = $_;
			}
			
			# not in line
		    } 
		    elsif ($found) {   # entry was found before, so advance $resIndex
			$resIndex++;
			$found=0;
			
			if ($resIndex != (scalar @res)) {
			    redo;
			} 
			else {
			    # got to end of res ids, so print
			    if (($model) && ($currseg == 1)) {
#				print OUTFILE ("MODEL\t$modelnum\n");    # changed, Gary 2/14/03
				print OUTFILE ("MODEL" . " " x (8 - length $modelnum) . "$modelnum\n");
			    }
			    
			    foreach (@datasofar) {
				print OUTFILE ($_);
			    }

			    if ($currseg == (scalar @segments)) {
				printTER($datasofar[scalar @datasofar -1]);
			    }

			    if (($model) && ($currseg == (scalar @segments))) {
				print OUTFILE ("ENDMDL\n");
			    }

			    $started=1;   #info has been put in

			    $started=1;
			    last;
			}
		    }
		}
		$currline++;
	    }

	    # got to end of line

	    # Gary added 11/11
	    # did not finish going through all residues in @res, try to skip current residue
	    if ($resIndex != (scalar @res)) {
		print("\nWarning: ", $sid ," : Could not find residue ", $res[$resIndex], " in PDBchain ", $chain->getName(),
		      " ; proceeding to next residue.\n");

		if ($resIndex < $#res) {
		    $resIndex++;       # skip current residue
		    $currline = 0;  # set current line to 0, it will increment up to nextgoodline later
		    goto LINELOOP;
		} 
		else {    # added rest of else clause to print the file if no more matches found, Gary 4/15/03

		    if (($model) && ($currseg == 1)) {
			# print OUTFILE ("MODEL\t$modelnum\n");    # changed, Gary 2/14/03
			print OUTFILE ("MODEL" . " " x (8 - length $modelnum) . "$modelnum\n");
		    }
		    
		    foreach (@datasofar) {
			print OUTFILE ($_);
		    }
		    
		    if ($currseg == (scalar @segments)) {
			if (scalar @datasofar != 0) {
			    printTER($datasofar[scalar @datasofar -1]);
			}
		    }
		    
		    if (($model) && ($currseg == (scalar @segments))) {
			print OUTFILE ("ENDMDL\n");
		    }
		    
		    $started=1;   #info has been put in
		}
	    }

	    if ($currline == scalar @PDBfile) {
		$maxoffset = $currline-1;
	    }

	    if (($resIndex != (scalar @res)) &&
		($resIndex != (scalar @res)-1) &&
		(!$started)) {
		close(OUTFILE);
		unlink("$filename");
		print("\nERROR - PDB lines do not match RAF IDs for domain ".$sid."\n\n");
		print("@res\n");
		print("resindex = $resIndex\n");
		printf("size of res = %s\n", scalar @res);
		print("current res = $res[$resIndex]\n");
		print("@datasofar\n");
		print( "PDB lines do not match RAF residue IDs, entry = $entry, PDBChainID = $PDBChainID\nlast good line = $lastline\n");
		last;
	    }
	}

	# if ((!defined $chain) || (defined $offset && $offset == -1)) {
	if ((!defined $chain) || (defined $offset && $offset == -1) || !$model) {  # Gary 11/21, reasoning: if not model, no need to 
	    # look for more occurrances of current segment
	    last;
	}
    }
    # close output file
    
    if ($started) {
	print OUTFILE ("END\n");
	close (OUTFILE);
    }
}

# Given last line in chain entry, prints out TER record
sub printTER {
    my ($atomnum, $rescode, $chainletter, $resnum) =
	($_[0] =~ /^.{6}\s*(\S+).{6}\s*(\S+) (.{1})\s*(\S+)\s+/);
    
    my $spaces = 8 - (length ++$atomnum);
    
    print OUTFILE ("TER");

    while ($spaces-- > 0) {
	print OUTFILE (" ");
    }
    
    print OUTFILE ("$atomnum      $rescode $chainletter");
    
    $spaces = 4 - (length $resnum);
    while ($spaces-- > 0) {
	print OUTFILE (" ");
    }
    
    print OUTFILE ("$resnum\n");
}

# stuffs contents of <PDBFile> into @PDBfile
sub getFileContents {
    my $revdat = undef;   # date in first REVDAT entry
    my $headdate = undef;
    my ($model, $firstmodelnum);

    undef @PDBfile;
    @PDBfile = <PDBFile>;

    while (!($PDBfile[0] =~ /^ATOM|^HETATM/)) {
	if (($PDBfile[0] =~ /^REVDAT/) && (!defined $revdat)) {
	    ($revdat) = ($PDBfile[0] =~ /\S+\s+\S+\s+(\S+)/);
	} elsif ($PDBfile[0] =~ /^HEADER/) {
	    ($headdate) = ($PDBfile[0] =~ /(.{2}-.{3}-.{2})/);
	} elsif ($PDBfile[0] =~ /^MODEL/) {
	    ($firstmodelnum) = ($PDBfile[0] =~ /^MODEL\s*(\S*)/);
	    $model=1;
	}

	shift @PDBfile;
	last if scalar(@PDBfile) == 0; # DZ, to escape from the potential eternal loop
    }

    return ($revdat, $headdate, $model, $firstmodelnum);
}

#ATOM   1306  CG  LYS A 162      20.233  27.984  25.413  1.00 50.55   1  201L1432

#advances @PDBfile to chain given in $_[0], returning the offset or -1 if chain not found
sub advanceToChain {
    my ($chainID, $offset, $resID, $model) = (@_);
    my $modelnum;

    my $isFirst = ($offset == 0) ? 1 : 0;   # Gary 11/21

#    print("stuff = $offset, $resID, $model, $isFirst\n");

    while (1) {
	# got to end of file, so return -1;
        if ($offset == (scalar @PDBfile)) {
            return -1;
        }

	if ($PDBfile[$offset] =~ /^MODEL/) {
	    ($modelnum) = ($PDBfile[$offset] =~ /^MODEL\s*(\S*)/);
	}

	# skip all ACE
	if ($PDBfile[$offset] =~ /^HETATM.+ACE/) {
	    $offset++;
	    next;
	}

	# skip all HOH
	if ($PDBfile[$offset] =~ /^HETATM.+HOH/) {
	    $offset++;
	    next;
	}

# Gary 11/21
#	if ($PDBfile[$offset] =~ /(^ATOM  |^HETATM).{15}$chainID\s*$resID\s+/) {
#	    return ($offset, $modelnum);
#	}
	# below Gary 11/21
	if ($isFirst || !$model) {
	    if ($PDBfile[$offset] =~ /(^ATOM  |^HETATM).{15}$chainID\s*$resID\s+/) {
		return ($offset, $modelnum);
	    }
	} else {  # not first entry, is model
	    # skip until get to MODEL
	    if ($PDBfile[$offset] !~ /^MODEL/) {
		$offset++;
		next;
	    }

	    # return $offset at line after MODEL line
	    return ($offset+1, $modelnum);
	}

	$offset++;

#	# got to end of file, so return -1;
#        if ($offset == (scalar @PDBfile)) {
#            return -1;
#        }
    }
}

sub inPDBfile {
    my ($chain,$id,$line) = (@_);
    if (($line =~ /^.{21}$chain\s*$id\s+/) && (!($line =~ /HOH/))) {
	return 1;
    }
    return 0;
}

# comp : arguments : $_[0] : a residue ID
#                    $_[1] : another residue ID
#        returns : -1 if $_[0] is less than $_[1]
#                   0 if $_[0] is same as $_[1]
#                   1 if $_[0] is greater than $_[1]
sub comp {
    my ($anum, $aletter) = ($_[0] =~ /\s*(\d*)(\w*)/);
    my ($bnum, $bletter) = ($_[1] =~ /\s*(\d*)(\w*)/);

    $aletter = uc $aletter;
    $bletter = uc $bletter;

    $anum = ($anum eq "") ? 0 : $anum;
    $bnum = ($bnum eq "") ? 0 : $bnum;

    if ($anum > $bnum) {
	return 1;
    } elsif ($anum < $bnum) {
	return -1;
    } elsif ($aletter gt $bletter) {
	return 1;
    } elsif ($aletter lt $bletter) {
	return -1;
    } else {
	return 0;
    }
}

sub init {
    my $help = "usage: pdbfilter1 -p <gzipped_pdb_file> -o <output_file> -r <RAF_file> -i <RAF_index> -s <SCOPe sid> -d <SCOPe node description> -u <SCOPe sunid> -c <SCOPe sccs> -a <comments> -b <spaci_line> -v <version> -P <Pfam_version> -A <ASTEROIDS header>\n";

    &getopts('p:o:r:s:d:u:c:a:b:v:i:P:A:');
    if (!defined $opt_p) {
	print("pdb file not given\n");
	die $help;
    }
    die "PDB file does not exist, $!\n" if (! (-e $opt_p));
    
    if (!defined $opt_o) {
	print("output file not given\n");
        die $help;
    }

    if (defined $opt_r) {
	die "RAF map file does not exist, $!\n" if (! (-e $opt_r));
    } else {
        die $help;
    }

    die "sid not given" if (!defined $opt_s);
    die "description not given" if (!defined $opt_d);
    die "sunid not given" if (!defined $opt_u);
    die "sccs not given" if (!defined $opt_c);
    die "spaci scores not given" if (!defined $opt_b);
    die "comments not given" if (!defined $opt_a);
    die "version not given" if (!defined $opt_v);
    die "raf index not given" if (!defined $opt_i);
}
