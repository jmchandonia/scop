#!/usr/bin/perl
#
#    Copyright (c) 2002-2018, The Regents of the University of California
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

# Version numbers
# 0.1.1 - Added function to checkSyntax function. (Gary 11/01/02)
# 0.1 - Initial version

package SCOPDom;

################################################################################
########                      PRIVATE STATE                           ##########
################################################################################

# name : SCOP domain name, ie "d1dlwa_"
# PDBCode : PDB code, ie "1dlw"
# chainID : chain's domain, ie "a"
# pageNum : SCOP page number, ie "1.001.001.001.001.001"
# verbose : verbose hierarchy in SCOP, ie "Class: All ..."


################################################################################
########                 CONSRUCTOR FUNCTIONS                         ##########
################################################################################

# new : arguments : $_[0] : a SCOP line
#       returns : new SCOPDom object
sub new {
    my $line = $_[0];
    chomp($line);
    my $self = {};

    my (@line) = split(/\s+/,$_[0],5);
     
    ($self->{name},
     $self->{PDBCode},
     $self->{chainID},
     $self->{pageNum},
     $self->{verbose}) = @line;

    bless($self);
    return $self;
}


################################################################################
########                   ACCESSOR FUNCTIONS                         ##########
################################################################################

# getName : arguments : none
#           returns : SCOP domain name
sub getName {
    my $self = shift;
    return $self->{name};
}

# getPDBCode : arguments : none
#              returns : PDB code
sub getPDBCode {
    my $self = shift;
    return $self->{PDBCode};
}

# getChainID : arguments : none
#              returns : chain ID
sub getChainID {
    my $self = shift;
    return $self->{chainID};
}

# getPageNum : arguments : none
#                  returns : SCOP page number
sub getPageNum {
    my $self = shift;
    return $self->{pageNum};
}

# getVerbose : arguments : none
#                  returns : SCOP page number, verbose form
sub getVerbose {
    my $self = shift;
    return $self->{verbose};
}


################################################################################
########                       MISC FUNCTIONS                         ##########
################################################################################

# nASTRALSeqsOS : arguments : none
#                 returns : number of unique chains
sub getnASTRALSeqsOS {
    $self = shift;

    my $id = $self->getChainID();
    my @id = split(/,/,$id);
    my ($count,$char1,$char2) = (1,"","");

    if (!($id[0] =~ /:/)) {  # no colons, then only 1
        return 1;
    }

    $char1 = substr($id[0],0,1);
    for (my $i=0 ; $i<=$#id ; $i++) {
        if ($char1 ne ($char2 = substr($id[$i],0,1))) {
            $char1 = $char2;
            $count++;
        }
    }
    return $count;
}

# getChainIDs : arguments : none
#               returns : REFERENCE to array of strings containing chain IDs
#                         of the form "a:" or "a:1-38", if the numbering is
#                         available
sub getChainIDs {
    my $self = shift;
    my @returnVal = split(/,/,$self->{chainID});
    return \@returnVal;
}

# printme : arguments : none
#                 returns : none
# Description : prints a description of the object
sub printme {
    my $self = shift;
    printf("%s\n",&getName($self));
    printf("%s\n",&getPDBCode($self));
    printf("%s\n",&getChainID($self));
    printf("%s\n",&getPageNum($self));
    printf("%s\n",&getVerbose($self));
}


################################################################################
########                    SEMANTIC FUNCTIONS                        ##########
################################################################################

# checkSyntax : arguments : $_[0] : a raf object
#               returns : undef if no syntax errors found in chain;
#                         a string containing error information otherwise
sub checkSyntax {
    my $self = shift;

    my ($raf) = @_;
    my ($chainID, $beg, $end, $chain, @map, $resid, $atomres, $seqres);
    my $errorString = "";

    foreach ( @{$self->getChainIDs()} ) {

	# find chainID, beg, end
	if (/:$/) {                                                         # looks like "a:"
	    ($chainID) = ($_ =~ /^(\S):$/);
	} elsif (/:/) {
	    ($chainID, $beg, $end) = ($_ =~ /^\s*(\S+):(\S+)-(\S+)\s*$/);   # looks like "a:2-136"
	} else {
	    $chainID = "_";
	}
	$chain = $raf->getChain( $self->getPDBCode() . uc ($chainID) );

	# critical error if can't find chain
	if (not defined $chain) {
	    $errorString .= "NOTEXIST: chain " . $self->getPDBCode() . uc($chainID) . " for domain " . $self->getName() . "\n";
	    return $errorString;
	}

	# find correct region of chain
	@map = $chain->getBody() =~ /(.{7})/g;
	if (defined $beg) {
	    while ($map[0] =~ /^\s*$beg\s*/) {
		shift @map;
	    }
	}
	if (defined $end) {
	    while ($map[$#map] =~ /^\s*$end\s*/) {
		pop @map;
	    }
	}

	# look for syntax error in correct region of chain
	my ($badseq, $badatom) = (0,0);
	foreach (@map) {
	    ($resid, $atomres, $seqres) = ($_ =~ /^\s*(\S+)\s*(\S)(\S)\s*$/);

	    if ( (not defined $resid) || (not defined $atomres) || (not defined $seqres) ) {
		$errorString .= "SYNTAX: " . $self->getName() . "\n";
		return $errorString;    # big error, return immediately
	    }

	    # look for missing residues
	    if ( ($atomres eq ".") ){ #&& ($resid !~ /[BME]/) ) {
#		$errorString .= "MISSINGATOM: " . $self->getName() . ", resid = " . $resid . "\n";
		$badatom++;
	    }
	    if ( ($seqres eq ".") ) { #&& ($resid !~ /[BME]/) ) {
#		$errorString .= "MISSINGSEQ " . $self->getName() . ", resid = " . $resid . "\n";
		$badseq++;
	    }
	}

	$errorString .= "MISSINGATOM: " . $self->getName() . "\n" if ($badatom);
	$errorString .= "MISSINGSEQ: " . $self->getName() . "\n" if ($badseq);

	# look for mismatches
	$mismatchString = $chain->getMismatch($beg, $end);
	if (defined $mismatchString) {
	    my ($chainname, $domname) = ($chain->getName(), $self->getName());
	    $mismatchString =~ s/$chainname/$domname/g;
	    $errorString .= $mismatchString;
	}
    }
    return ($errorString eq "") ? undef : $errorString;
}

1;
