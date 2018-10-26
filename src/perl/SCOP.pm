#!/usr/bin/perl
#
#    Copyright (c) 2002, The Regents of the University of California
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

=head2 revisions 
 0.1.7 - Fixed some comments that gave global-font-lock trouble. (Gary 10/29/02)
 0.1.6 - fixed same bug in rafseq --JMC (10/28/02)
 0.1.5 - fixed small bug in getASTRALseqGD; check return value first --JMC
 0.1.4 - fixed extra flag added to getASTRALseq*
 0.1.3 - added extra flag to getASTRALseq* to get classes 8+
 0.1.2 - "." trimmed off output in both atomres AND seqres
 0.1.1 - getASTRALseq* now returns no "." residues; "B" and "E" residues are
         ignored
 0.1 - Initial version

=cut

package SCOP;

use FileHandle;
use CDB_File;
use SCOPDom;

################################################################################
########                      PRIVATE STATE                           ##########
################################################################################

# SCOPFile;             # SCOP directory file
# indexFile;            # index file for SCOPFile
# idx;                  # index hash

# for creation of index file
# indexSkip;            # skip line if it starts with indexSkip
                        #   (ie comment char)
# indexKeyStart;        # key starts at char indexKeyStart (counting from 0)
# indexKeyLength;       # key length


################################################################################
########                   SCOP MODULE ACCESSORS                      ##########
################################################################################

# getIndexFile : arguments : none
#                returns : index file
sub getIndexFile {
    my $self = shift;
    return $self->{indexFile};
}

# getLogFile : arguments : none
#              returns : log file
sub getLogFile {
    my $self = shift;
    return $self->{logFile};
}

# getSCOPFile : arguments : none
#               returns : SCOP file
sub getSCOPFile {
    my $self = shift;
    return $self->{SCOPFile};
}

# getIndexHash : arguments : none
#                returns : index hash
sub getIndexHash {
    my $self = shift;
    return %{ $self->{idx} };
}

# getIndexCommentChar : arguments : none
#                       returns : index comment character
sub getIndexCommentChar {
    my $self = shift;
    return $self->{indexSkip};
}

# getIndexKeyStart : arguments : none
#                    returns : index key start
sub getIndexKeyStart {
    my $self = shift;
    return $self->{indexKeyStart};
}

# getIndexKeyLength : arguments : none
#                     returns : index key length
sub getIndexKeyLength {
    my $self = shift;
    return $self->{indexKeyLength};
}

# printme : arguments : none
#           returns : nothing
# Description: prints a description of the SCOP object
sub printme {
    my $self = shift;
    printf("indexFile = %s\n",$self->getIndexFile());
    printf("logfile = %s\n",$self->getLogFile());
    printf("raffile = %s\n",$self->getSCOPFile());
    printf("indexcommentchar = %s\n",$self->getIndexCommentChar());
    printf("indexkeystart = %s\n",$self->getIndexKeyStart());
    printf("indexkeylength = %s\n",$self->getIndexKeyLength());
}


################################################################################
########                   SCOP MODULE MODIFIERS                      ##########
################################################################################

# setIndexFile : arguments : $_[0] : new name for index file
#                returns: nothing
# if index currently tied, write it to existing index file and untie it ;
# if file exists, tie it, otherwise make a new index hash
sub setIndexFile {
    my $self = shift;
    my $newfile = $_[0];

    # if %idx currently tied, write it to existing index file and untie it
    if (defined $self->{idx}) {
        $self->makeIndexFile($self->{indexFile});
        untie %{ $self->{idx} };
    }

    # if file exists, tie it, otherwise make a new index hash
    if (-e $newfile) {
        $self->tieIndexHash($newfile);
    } else {
        $self->{idx} = $self->makeIndex();
        $self->makeIndexFile($newfile);
    }

    $self->{indexFile} = $newfile;
}

# setLogFile : arguments : $_[0] : new name for log file
#              returns: nothing
sub setLogFile {
    my $self = shift;
    $self->{logFile} = $_[0] if defined $_[0];
}

# setIndexCommentChar : arguments $_[0] : new value for index skip
#                       returns : nothing
sub setIndexCommentChar {
    my $self = shift;
    $self->{indexSkip} = $_[0] if (defined $_[0]);
}

# setIndexKeyStart : arguments $_[0] : new value for index key start,
#                                      -1 to ignore
#                    returns : nothing
sub setIndexKeyStart {
    my $self = shift;
    $self->{indexKeyStart} = $_[0] if (defined $_[0]);
}

# setIndexKeyLength : arguments $_[0] : new value for index key length,
#                                       -1 to ignore
#                     returns : nothing
sub setIndexKeyLength {
    my $self = shift;
    $self->{indexKeyLength} = $_[0] if (defined $_[0]);
}

# setSCOPFile : arguments : $_[0] : new name for SCOP file
#               returns: nothing, undefines index hash
sub setSCOPFile {
    my $self = shift;

    if (defined $_[0]) {
	if (! (-e $_[0])) {
	    die "SCOP directory file $_[0] does not exist, $!";
	}

	$self->{SCOPFile} = $_[0];
    }

    undef $self->{idx};
}


################################################################################
########                  SCOP INDEXING FUNCTIONS                     ##########
################################################################################

# logmsg
sub logmsg {
    print LOG @_;
}

# makeIndex : arguments : none
#             returns: index made from SCOP map file
sub makeIndex {
    my $self = shift;
    my %returnVal = ();
    my ($filepos, $key, $line);

    open (LIN,"$self->{SCOPFile}");
    open (LOG,">$self->{logFile}");

    $filepos = 0;
    while ($line = <LIN>) {

        if ((defined $self->{indexSkip}) && ($line =~ /^$self->{indexSkip}/o)) {
            &logmsg ("W: skipping $line");
            $filepos = tell(LIN);
            next;
        }

        if (($self->{indexKeyStart} != -1) && ($self->{indexKeyLength} != -1)) {
            if (($self->{indexKeyLength} - $self->{indexKeyStart}) > length ($line)) {

                chomp $line;
                die "E: key length ($self->{indexKeyStart},$self->{indexKeyLength}) exceeds record length (",
                length($line),") |", substr ($line,0,10),"..|\n";
            }
            $key = substr($line,$self->{indexKeyStart},$self->{indexKeyLength});
        } else { ($key) = $line =~ /^(\S+)/; }

        $returnVal{$key} .= " " if defined $returnVal{$key};    # accept duplicate key
        $returnVal{$key} .= $filepos;
        $filepos = tell(LIN);
    }

    close(LIN); close(LOG);
    return \%returnVal;
}

# makeIndexFile : arguments: $_[0] : index file (opt)
# Returns nothing.
# Default for index file is "index.idx"
sub makeIndexFile {
    my $self = shift;
    if (!defined $self->{idx}) {
        die "index hash not given: $!\n";
    }

    if (defined $_[0]) {
      CDB_File::create %{ $self->{idx} }, "$_[0]", "$_[0].tmp" ||
          die "Couldn't make $_[0]: $!\n";
    } else {
      CDB_File::create %{ $self->{idx} }, "$self->{indexFile}", "$self->{indexFile}.tmp" ||
          die "Couldn't make $self->{indexFile}: $!\n";
    }
}

# tieIndexHash : arguments : $_[0] : index file string
#                returns: nothing
# Description: ties the index hash to the index file
sub tieIndexHash {
    my $self = shift;
    tie %{ $self->{idx} }, 'CDB_File', $_[0] or die "Could not tie $self->{indexFile}: $!\n";
}


################################################################################
########                    SCOP MODULE FUNCTIONS                     ##########
################################################################################

# new: arguments: $_[0] : a SCOP file (ie dir.lin.scop.txt-1.57)          (opt)
#                 $_[1] : an index file (ie indexS.idxS)                  (opt)
#                 $_[2] : index comment character                         (opt)
#                 $_[3] : index key start                                 (opt)
#                 $_[4] : index key length                                (opt)
#      returns: new SCOP object
# First 2 arguments are optional. If none specified, use defaults.
#                                 If only first specified, we create the index
#                                    hash from the second argument.
#                                 If first two specified, we use them.
# Last 3 arguments must either all be specified or all not specified.
# Use undef values if need be.
sub new {
    my ($j,$k);
    my $self;

    #default values
    $self->{SCOPFile} = "dir.lin.scop.txt-1.57";
    $self->{indexFile} = "indexS.idx";
    $self->{logFile} = "logS.txt";
    $self->{idx} = {};
    $self->{indexSkip} = "#";
    $self->{indexKeyStart} = -1;
    $self->{indexKeyLength} = -1;

    bless ($self);

    # set all index values
    if (defined $_[2]) {
        $self->setIndexCommentChar($_[2]);
        $self->setIndexKeyStart($_[3]);
        $self->setIndexKeyLength($_[4]);
    }

    # if first 2 defined, set defaults and proceed
    # if index file exists, uses it ; otherwise creates it
    if (($j = defined $_[0]) && ($k = defined $_[1])) {
        $self->setSCOPFile($_[0]);
        $self->setIndexFile($_[1]);
    }

    # if first defined, set defaults and make index;
    # does not make new index hash
    elsif ($j && !$k) {
        $self->setSCOPFile($_[0]);
        $self->{idx} = $self->makeIndex();
    }

    # if none defined, then use defaults to make index;
    # if default index file exists, uses it ; otherwise, makes it
    elsif (!$j && !$k) {
        $self->setSCOPFile();
	$self->setIndexFile($self->{indexFile});
    }

    else {
        die "Invalid arguments to SCOP::new()\n";
    }

    return $self;
}

# cleanup : arguments : none
#           returns : nothing
# Description: does housekeeping of RAF module, like untying index hash
sub cleanup {
    my $self = shift;
    untie %{ $self->{idx} } if (defined %{ $self->{idx} });
    undef $self->{idx};
}

# getDomain : arguments : $_[0] : a SCOP Domain (ie "d1coha_")
#             returns : new SCOPDom object representing $_[0], or
#                       undefined if no such object exists
sub getDomain {
    my $self = shift;
    my $line = $self->SCOPLookup($_[0]);

    if (defined $line) {
        return SCOPDom::new($line);
    } else {
        return ();
    }
}

# getDomains : arguments : $_[0] : an index (ie "1coh" if indexing length is 4)
#              returns : an array of new SCOPDom objects containing $_[0], or
#                        undefined if no such object exists
sub getDomains {
    my $self = shift;
    my @domList = ();

    my $line;
    my $i=0;

    my $val = ${$self->{idx}}{$_[0]};

    if (!defined $val) {
      return;
    }

    open(LIN,"$self->{SCOPFile}");

    my @seekLoc = split(/ +/,$val);
    foreach (@seekLoc) {
        seek(LIN,$_,0);
        $line = <LIN>;
        $domList[$i++] = SCOPDom::new($line);
    }

    close(LIN);

    return \@domList;
}


# getAllDomains : arguments : none
#                 returns : REFERENCE TO array of all SCOPDom objects in
#                           current SCOP object in order of appearance in SCOPFile
sub getAllDomains {
    my $self = shift;
    open(LIN,"$self->{SCOPFile}");

    my $line;
    my @domains;
    my $i = 0;

    while($line = <LIN>) {
	chomp $line;
	$domains[$i++] = SCOPDom::new($line);
    }

    close(LIN);

    return \@domains;
}

# SCOPLookup : arguments : $_[0] : a SCOP domain name
#              returns : a SCOP line representing $_[0]
sub SCOPLookup {
    my $self = shift;

    my $seekLoc = ${ $self->{idx} }{$_[0]};

    if (!defined $seekLoc) {
	return;
    }

    open(LIN,"$self->{SCOPFile}");
    seek(LIN,$seekLoc,0);
    my $line = <LIN>;
    close(LIN);

    return $line;
}

# inSCOP : arguments : $_[0] : domain name
#          returns : 1 if ID is in $SCOPFile, 0 otherwise
sub inSCOP {
    my $self = shift;
    return (defined ${$self->{idx}}{$_[0]});
}


################################################################################
########                    SEQUENCING FUNCTIONS                      ##########
################################################################################

# getASTRALseqsOS : arguments : $_[0] : SCOPDom object
#                               $_[1] : RAF object
#                               $_[2] : 1 for atomres, 0 for seqres
#                               $_[3] : 0 for normal, 1 for classes 8+
#                   returns two REFERENCES to arrays:
#                             1) REFERENCE to array of description strings
#                             2) REFERENCE to array of sequence strings
#                             3) REFERENCE to array of residue ID strings
# Note: returns () if errors found
sub getASTRALseqsOS {
    my $self = shift;

    my ($scopdom,$raf,$seqOption,$ntcOption) = @_;
    $ntcOption = 0 if not defined $ntcOption;
    my $n = $scopdom->getnASTRALSeqsOS();

    my ($seq,$desc,$resid);    
    my (@desc,@seq,@resid);

    for (my $i=0 ; $i < $n ; $i++) {
	($desc,$seq,$resid) = $self->getASTRALseqOS($scopdom,$raf,$i+1,$seqOption,$ntcOption);

	if ((!defined $desc) || (!defined $seq)) {
	    return ();
	}

	$desc[$i] = $desc;
	$seq[$i] = $seq;
	$resid[$i] = $resid;
    }

    return (\@desc,\@seq,\@resid);
}

# getASTRALseqOS : arguments : $_[0] : SCOPDom object
#                              $_[1] : RAF object
#                              $_[2] : chain number (counting from 1,
#                                        must be in range of $_[0])
#                              $_[3] : 1 for atomres, else seqres
#                              $_[4] : 0 for normal, 1 for domains 8+
#                   returns two strings:
#                             1) description string
#                             2) sequence string
#                             3) string of residue IDs
# Note: returns () if errors found
sub getASTRALseqOS {
    my $self = shift;

    my ($scopdom,$raf,$chainnum,$seqOption,$ntcOption) = @_;
    $ntcOption = 0 if not defined $ntcOption;

    my $n = $scopdom->getnASTRALSeqsOS();
    my @chains = split(/,/,$scopdom->getChainID());
    my @aa = getSlice($chainnum,@chains);

    my ($desc,$seq,$resid) = $self->rafseq($scopdom,$raf,$seqOption,$n,\@aa,$ntcOption);

    return ($desc,$seq,$resid);
}

# getSlice : arguments : $_[0] : chain number
#                        $_[1] : array of chains
#            returns : array slice of $_[1] containing all elements that begin
#                      with same character, starting at the $_[0]th unique character
sub getSlice {
    my ($chainNum,@chains) = @_;
    my ($position, @returnVal) = (0);

    if (!($chains[0] =~ /:/)) {
	return @chains;
    }
    $char = substr($chains[0],0,1);

    while ($chainNum != 1) {
	while (($position <= $#chains) && ($chains[$position] =~ /$char/)) {
	    $position++;
	}
	$chainNum--;
	if ($position > $#chains) {
	    last;
	}
	$char = substr($chains[$position],0,1);
    }

    while (($position <= $#chains) && ($chains[$position] =~ /$char/)) {
	$returnVal[$chainNum-1] = $chains[$position];
	$chainNum++;
	$position++;
    }
    return @returnVal;
}

# rafseq : arguments : $_[0] : SCOPDom object
#                      $_[1] : RAF object
#                      $_[2] : 1 if atomres, 0 if seqres
#                      $_[3] : number of chains
#                      $_[4] : @dd
#                      $_[5] : 0 for normal, 1 for classes 8+
#          returns : 1) description string
#                    2) sequence string
#                    3) string of residue IDs
sub rafseq {
    my $self = shift;

    my ($sid, $pdb, $dom, $class, $lin, $prot, $spec, $desc);
    my (%seq);

####CHANGED 10/15
#   my ($scopdom,$raf,$seqOption,$n,@dd, $ntcOption) = @_;
    my ($scopdom,$raf,$seqOption,$n,$dd_r,$ntcOption) = @_;
    
    my (@dd) = (@$dd_r);
    
    $ntcOption = 0 if not defined $ntcOption;

    $sid = $scopdom->getName();
    $pdb = $scopdom->getPDBCode();
    $dom = $scopdom->getChainID();
    $class = $scopdom->getPageNum();
    $lin = $scopdom->getVerbose();

    open (LOG,">>$self->{logFile}");

    # only use structures with coordinates
#    if ($pdb =~ /^[s0]/) {
#	return ();
#    }

    $class =~ s/\.0*/\./g;
    $class =~ s/^1\.//;
    ($spec) = $lin =~ /Species: (.*)\|/;
    $spec = "-" if !defined $spec;
    $spec =~ s/<(.*?)>//g;
    ($prot) = $lin =~ /Protein: ([^|]*)/;
    $prot = "-" if !defined $prot;

    if (($ntcOption == 0) && ($class !~ /^[1-7]\./)) {
	logmsg "Omitting $sid: class $class\n";
	return ();
    }
    elsif (($ntcOption == 1) && ($class =~ /^[1-7]\./)) {
	logmsg "Omitting $sid: class $class\n";
	return ();
    }

    undef %seq; undef $seq;
    my ($dd, $sid0, $aeid);

    foreach $dd (@dd) {
	my ($chain, $key, $beg, $end);
	my ($map, $ok, $seqres, $atomres,$resid);
	
	($chain) = $dd =~ /^(.):/;
	$chain = "_" if !defined $chain;    # null chain id is '_'
	
	$key = "$pdb$chain";

        if (not $raf->inRAF($key)) {
	    $chain = uc($chain);
	    $key = "$pdb$chain";
	}

	if (not $raf->inRAF($key)) {
	    &logmsg ("Omitting $sid: missing mapping (", $raf->getRAFFile(), ")\n");
	    return ();
	}
	
	undef $beg; undef $end;
	($beg, $end) = $dd =~ /(-?\d+[A-Za-z]?)-(-?\d+[A-Za-z]?)/o;
	if ((defined $beg) && (not defined $end) ||
	    (not defined $beg) && (defined $end)) {
	    &logmsg ("Omitting $sid: $beg or $end parsing error ($dd)\n");
	    return ();
	}
	
	$aeid = "e" . substr($sid,1,6) . $chain;
	$seq{dom}{$aeid} .= "," if $seq{dom}{$aeid};
	# accept (-) and ([a-z]:)
	# NOTE: this ^^^^^^^^^^^ will include 'chain id:' in ALL dom def
	($seq{dom}{$aeid}) .= $dd;

	my $chainobj = $raf->getChain($key);
	if (!defined $beg && !defined $end) {
	    $beg = $chainobj->getFirstResID();
	    $end = $chainobj->getLastResID();
	}
	($ok, $seqres, $atomres,$resid) = $chainobj->rafseqHelper($beg, $end, 1,  # 1 to include internal missing res
								  undef, 1);
#	($ok, $seqres, $atomres,$resid) = $chainobj->rafseqHelper($beg, $end, undef,
#								  undef, 1);

        if ($ok eq 0) {
            &logmsg ("Omitting $sid: wrong $beg or $end\n");
            return ();
        }

	# trim "."
	$atomres =~ s/\.//g;
	$seqres =~ s/\.//g;

	$seq{seq}{$aeid} .= "X" if defined $seq{seq}{$aeid};
	if ($seqOption != 1) {
	    $seq{seq}{$aeid} .= $seqres;
	} else {
	    $seq{seq}{$aeid} .= $atomres;
	}

	$seq{resid}{$aeid} .= $resid;
    }

    $sid0 = $sid;
    $nchains = $n;
    if ($nchains > 1) { $sid0 = $aeid; }

    $desc = "$sid0 $class ($seq{dom}{$aeid}) $prot {$spec}";
    
    my $sequence = $seq{seq}{$aeid};   #get sequence for this fragment
    my $totalresid = $seq{resid}{$aeid};
    $totalresid = substr($totalresid,0, (length $totalresid) - 1);

    close LOG;
    return ($desc,$sequence,$totalresid);
}

# getASTRALseqGD : arguments : $_[0] : SCOPDom object
#                              $_[1] : RAF object
#                              $_[2] : 1 if atomres, 0 if seqres
#                              $_[3] : 0 if normal, 1 if classes 8+
#                  returns : 1) description string
#                            2) sequence string
#                            3) string of residue IDs
sub getASTRALseqGD {
    my $self = shift;

    my ($sid, $pdb, $dom, $class, $lin, $prot, $spec, $desc);
    my (@dd);
    my $sequence = "";
    my $resid = "";

    my ($scopdom,$raf,$seqOption,$ntcOption) = @_;
    $ntcOption = 0 if not defined $ntcOption;
    $sid = $scopdom->getName();
    $pdb = $scopdom->getPDBCode();
    $dom = $scopdom->getChainID();
    $class = $scopdom->getPageNum();
    $lin = $scopdom->getVerbose();

    open (LOG,">>$self->{logFile}");

    # only use structures with coordinates
#    if ($pdb =~ /^[s0]/) {
#	return();  
#    }

    $class =~ s/\.0*/\./g;
    $class =~ s/^1\.//;
    ($spec) = $lin =~ /Species: (.*)\|/;
    $spec = "-" if !defined $spec;
    $spec =~ s/<(.*?)>//g;
    ($prot) = $lin =~ /Protein: ([^|]*)/;
    $prot = "-" if !defined $prot;
    $desc = "$sid $class ($dom) $prot {$spec}";

    if (($ntcOption == 0) && ($class !~ /^[1-7]\./)) {
	logmsg "Omitting $sid: class $class\n";
	return ();
    }
    elsif (($ntcOption == 1) && ($class =~ /^[1-7]\./)) {
	logmsg "Omitting $sid: class $class\n";
	return ();
    }

    @dd = split (/,/,$dom);
    my ($dd);

    foreach $dd (@dd) {
	my ($chain, $key, $beg, $end);
	my ($map);
	
	($chain) = $dd =~ /^(.):/;        # one ch chain id
	$chain = "_" if !defined $chain;  # null chain is '_'
	
	$key = "$pdb$chain";

        if (not $raf->inRAF($key)) {
            $chain = uc($chain);
	    $key = "$pdb$chain";
	}

	if (not $raf->inRAF($key)) {
	    &logmsg ("Omitting $sid: missing mapping ($raf->getRAFFile())\n");
	    last;
	}
	
	undef $beg; undef $end;
	($beg, $end) = $dd =~ /(-?\d+[A-Za-z]?)-(-?\d+[A-Za-z]?)/o;
	if ((defined $beg) && (not defined $end) ||
	    (not defined $beg) && (defined $end)) {
	    &logmsg ("Omitting $sid: $beg or $end parsing error ($dd)\n");
	    last;
	}

	my $chainobj = $raf->getChain($key);
	if (!defined $beg && !defined $end) {
	    $beg = $chainobj->getFirstResID();
	    $end = $chainobj->getLastResID();
	}
	my ($ok, $fragmentSeqres, $fragmentAtomres,$fragmentResid) = $chainobj->rafseqHelper($beg, $end, 1, # include M's
											     undef, 1);
#	my ($ok, $fragmentSeqres, $fragmentAtomres,$fragmentResid) = $chainobj->rafseqHelper($beg, $end, undef,
#											     undef, 1);

	# trim "."
	if ($ok eq 0) {
	    &logmsg ("Omitting $sid: wrong $beg or $end\n");
	    last;
	}

	$fragmentAtomres =~ s/\.//g;
        $fragmentSeqres =~ s/\.//g;

	if ($seqOption == 0) {
	    $sequence .= "$fragmentSeqres" . "X";
	} elsif ($seqOption == 1) {
	    $sequence .= "$fragmentAtomres" . "X";
	} else {
	    die "seqOption must be 0 or 1.$!\n";
	}

	$resid .= $fragmentResid;
    }   # for each fragment

    # get rid of trailing "X" or space
    $sequence = substr($sequence,0,(length $sequence) - 1);
    $resid = substr($resid,0,(length $resid) - 1 );

    if ($sid =~ m/\./) {
	$desc =~ s/^d/g/;
    }

    close LOG;
    return ($desc, $sequence,$resid);
}

1;
