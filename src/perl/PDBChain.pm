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
# 1.0 - removed binary search in getResAbs
#       fixed residue parser to parse ranges like -2--1
#       fixed comp to work with negative residue numbers (JMC, 4/27/16)
# 0.1.8 - added more checks to checkSyntax
# 0.1.7 - fixed bug in getSeq; didn't strip . properly
# 0.1.6 - made getSeq return whole chain by default, not just truncated
#         to first and last residues
# 0.1.5 - Added functions for error checking : getMismatch, hashMisMatch,
#          checkSyntax. (Gary 11/1/02)
# 0.1.4 - Known bug: nMissing only counts "M" resides as missing, not "B" or "E".
# 0.1.3 - Fixed some comments that gave global-font-lock trouble. (Gary 10/29/02)
# 0.1.2 - getASTRAL* with range = "-" will give all residues BUT the B and E
#         missing residues; getMap* with range = "-" will give all residues
#         (including (B,M,E) residues).
# 0.1.1 - added additional comments: getFirstResID() and getLastResID() return
#         the first/last ATOM residues
# 0.1 - Initial version

=head1 NAME

    PDBChain.pm - Protein Data Bank Chain module

=head1 SYNOPSIS

    Perl Module

=head1 DESCRIPTION

Class PDBChain's job is to manage the internal data of a chain object, which
includes the chain's: 
 * PDB+chain ID 
 * SEQRES and ATOMRES sequences 
 * residue IDs 
 * time stamp 

It is the responsibility of class RAF to handle creation of PDBChain objects. As
such, the user should not manually make a PDBChain object. 

=cut

package PDBChain;

################################################################################
########                  OBJECT PRIVATE STATE                        ##########
################################################################################

#name           # PDB+Chain ID for chain
#version        # version number of RAF
#headerLength   # header length, constant for given RAF version
#timeStamp      # PDB timestamp
#bitFlags       # bit 1: mapped, 2: active, 3: checked, 4: manually edited
                #     5: ok, 6: one-to-one mapping
#firstResID     # first ATOM resid (pdb format, 4ch+1 for insertion code),
                #   includes leading and trailing spaces
#lastResID      # last ATOM resid (pdb format, 4ch+1 for insertion code),
                #   includes leading and trailing spaces
#body           # string of residue ids followed by residues, including
                #   leading spaces
#currentRes     # index of next residue to return in getNextRes, counting
                #   from 0

################################################################################
########                     CONSTRUCTORS                             ##########
################################################################################

# new : arguments : $_[0] : a RAF line
#       returns : new chain object
sub new {

    my $beg0 = 28;        # position of first atom resid
    my $end0 = 33;        # position of second atom resid
    my $HEADLEN = 38;

    my $line = $_[0];
    my @line = split(/\s+/,$line,8);
    my $self = {};
    $self->{name} = $line[0];
    $self->{version} = $line[1];
    $self->{headerLength} = $line[2];
    $self->{timeStamp} = $line[3];
    $self->{bitFlags} = $line[4];
    $self->{firstResID} = substr($line,$beg0,5);
    $self->{lastResID} = substr($line,$end0,5);
    $self->{body} = substr($line,$HEADLEN);       #getBodyfromLine($line);
    $self->{currentRes} = 0;

    bless($self);
    return $self;
}


################################################################################
########                 RAF OBJECT SELECTOR FUNCTIONS                ##########
################################################################################

=head2 getName()

 Usage   : $chain->getName()
 Returns : name of chain in string form
 Args    : none

 Returns the name of the chain, i.e. 1abcA

 Sample use:

  my $raf = RAF::new();

  # get the hemoglobin chain
  my $chain = $raf->getChain("101m_");

  printf("the chain is %s\n",$chain->getName());

=cut

# getName : arguments : none
#           returns: chain+PDB id
sub getName {
    my $self = shift;
    return $self->{name};
}

=head2 getVersion()

 Usage   : $chain->getVersion()
 Returns : RAF version
 Args    : none

 Returns the version of the RAF used to create the chain. 

 Sample use:

  my $raf = RAF::new();
  my $chain = $raf->getChain("101m_");
  printf("the version is %s\n",$chain->getVersion());

=cut

# getVersion : arguments : none
#              returns: RAF map version
sub getVersion {
    my $self = shift;
    return $self->{version};
}

=head2 getHeaderLength()

 Usage   : $chain->getHeaderLength()
 Returns : size of header
 Args    : none

 Returns the header length of a chain. The header length comes from a line of raw
 data from the RAF map file. 

 Sample use:

  my $raf = RAF::new();

  # get the hemoglobin chain
  my $chain = $raf->getChain("101m_");

  printf("the first header length is %s\n",$chain->getHeaderLength());

=cut

# getHeaderLength : arguments : none
#                   returns: header length
sub getHeaderLength {
    my $self = shift;
    return $self->{headerLength};
}

=head2 getTimeStamp()

 Usage   : $chain->getTimeStamp()
 Returns : time stamp
 Args    : none

 Returns the PDB time stamp of the chain. 

 Sample use:

  my $raf = RAF::new();
  my $chain = $raf->getChain("101m_");
  printf("the timestamp is %s\n",$chain->getTimeStamp());

=cut

# getTimeStamp : arguments : none
#                returns: PDB timestamp
sub getTimeStamp {
    my $self = shift;
    return $self->{timeStamp};
}

=head2 getBitFlags()

 Usage   : $chain->getBitFlags()
 Returns : bit flags
 Args    : none

 Returns the bit flags of the chain.

 Sample use:

  my $raf = RAF::new();
  my $chain = $raf->getChain("101m_");
  printf("the bitflags is is %s\n",$chain->getBitFlags());

=cut

# getBitFlags : arguments : none
#               returns: PDB timestamp
sub getBitFlags {
    my $self = shift;
    return $self->{bitFlags};
}

=head2 isMapped()

 Usage   : $chain->isMapped()
 Returns : 0 or 1
 Args    : none

 Returns 1 if the chain is mapped, 0 otherwise. 

 Sample use:

  my $raf = RAF::new();
  my $chain = $raf->getChain("101m_");
  printf("chain is %smapped\n", ($chain->isMapped()) ? "" : "not ");

=cut

# isMapped : arguments : none
#            returns: 1 if mapped bit is 1, 0 otherwise
sub isMapped {
    my $self = shift;
    return substr($self->{bitFlags},0,1);
}

=head2 isActive()

 Usage   : $chain->isActive()
 Returns : 0 or 1
 Args    : none

 Returns 1 if the chain is checked, 0 otherwise. 

 Sample use:

  my $raf = RAF::new();
  my $chain = $raf->getChain("101m_");
  printf("chain is %sactive\n", ($chain->isActive()) ? "" : "not ");

=cut

# isActive : arguments : none
#            returns: 1 if active bit is 1, 0 otherwise
sub isActive {
    my $self = shift;
    return substr($self->{bitFlags},1,1);
}

=head2 isChecked()

 Usage   : $chain->isChecked()
 Returns : 0 or 1
 Args    : none

 Returns 1 if the chain is checked, 0 otherwise. 

 Sample use:

  my $raf = RAF::new();
  my $chain = $raf->getChain("101m_");
  printf("chain is %schecked\n", ($chain->isChecked()) ? "" : "not ");

=cut

# isChecked : arguments : none
#             returns: 1 if checked bit is 1, 0 otherwise
sub isChecked {
    my $self = shift;
    return substr($self->{bitFlags},2,1);
}

=head2 isManuallyEdited()

 Usage   : $chain->isManuallyEdited()
 Returns : 0 or 1
 Args    : none

 Returns 1 if the chain is manually edited, 0 otherwise. 

 Sample use:

  my $raf = RAF::new();
  my $chain = $raf->getChain("101m_");
  printf("chain is %smanually edited\n", ($chain->isManuallyEdited()) ? "" : "not ");

=cut

# isManuallyEdited : arguments : none
#                    returns: 1 if manually edited bit is 1, 0 otherwise
sub isManuallyEdited {
    my $self = shift;
    return substr($self->{bitFlags},3,1);
}

=head2 isOk()

 Usage   : $chain->isOk()
 Returns : 0 or 1
 Args    : none

 Returns 1 if the chain is OK, 0 otherwise. 

 Sample use:

  my $raf = RAF::new();
  my $chain = $raf->getChain("101m_");
  printf("chain is %sOK\n", ($chain->isOk()) ? "" : "not ");

=cut

# isOk : arguments : none
#               returns: 1 if OK bit is 1, 0 otherwise
sub isOk {
    my $self = shift;
    return substr($self->{bitFlags},4,1);
}

=head2 isOneToOneMap()

 Usage   : $chain->isOneToOneMap()
 Returns : 0 or 1
 Args    : none

 Returns 1 if the chain is a one to one map, 0 otherwise.
 NOTE: one-to-one-mapping only means that there is a one-to-one mapping between
 SEQRES and ATOM sequences. It does not mean the sequences are the same, only
 that all the residues are seen. 

Sample use:

  my $raf = RAF::new();
  my $chain = $raf->getChain("101m_");
  printf("chain is %sone to one map\n", ($chain->isOneToOneMap()) ? "" : "not ");

=cut

# isOneToOneMap : arguments : none
#                 returns: 1 if one-to-one mappping bit is 1, 0 otherwise
sub isOneToOneMap {
    my $self = shift;
    return substr($self->{bitFlags},5,1);
}

=head2 getFirstResID()

 Usage   : $chain->getFirstResID()
 Returns : first residue ID
 Args    : none

 Returns the first ATOM residue ID in the chain. Note: Residue IDs are usually
 numbers, but can on occasion take other forms such as numbers concatenated with
 letters. 

 Sample use:

  my $raf = RAF::new();

  # get the hemoglobin chain
  my $chain = $raf->getChain("101m_");

  printf("the first residue ID is %s\n",$chain->getFirstResID());

=cut

# getFirstResID : arguments : none
#                 returns: first residue num
sub getFirstResID {
    my $self = shift;
    my ($returnVal) = $self->{firstResID} =~ /\s*(\S+)\s*/;
    return $returnVal;
}

=head2 getLastResID()

 Usage   : $chain->getLastResID()
 Returns : last residue ID
 Args    : none

 Returns the last ATOM residue ID in the chain. Note: Residue IDs are usually
 numbers, but can on occasion take other forms such as numbers concatenated
 with letters. 

 Sample use:

  my $raf = RAF::new();

  # get the hemoglobin chain
  my $chain = $raf->getChain("101m_");

  printf("the last residue ID is %s\n",$chain->getLastResID());

=cut

# getLastResID : arguments : none
#                returns: last residue number
sub getLastResID {
    my $self = shift;
    my ($returnVal) = $self->{lastResID} =~ /\s*(\S+)\s*/;
    return $returnVal;
}

=head2 getBody()

 Usage   : $chain->getBody()
 Returns : body of RAF entry
 Args    : none

 Returns the raw "body" portion of the chain object, which consists of residue
 IDs and SEQRES/ATOMRES sequences. Note: Raw body includes leading spaces. 

 Sample use:

  my $raf = RAF::new();

  # get the hemoglobin chain
  my $chain = $raf->getChain("101m_");

  printf("the body is %s\n",$chain->getBody());

=cut

# getBody : arguments : none
#           returns: body, including leading spaces,
#                    to be compatible with previous code
sub getBody {
    my $self = shift;
    return $self->{body};
}

=head2 getCurrentRes()

 Usage   : $chain->getCurrentRes()
 Returns : current residue
 Args    : none

 Returns the current residue number. Note: The current residue number is
 initially set to 0 and is incremented with each call to getNextRes(). It can be
 reset to 0 by calling resetCurrentRes(). 

 Sample use:

  my $raf = RAF::new();

  # get the hemoglobin chain
  my $chain = $raf->getChain("101m_");

  printf("the current residue number is %s\n",$chain->getCurrentRes());

=cut

# getCurrentRes : arguments : none
#                 returns: current residue index
sub getCurrentRes {
    my $self = shift;
    return $self->{currentRes};
}

# getBodyfromLine : arguments : a RAF line
#                   returns: body, including leading spaces,
#                    to be compatible with previous code
# Note : requires that $_[0] is in the RAF index
sub getBodyfromLine {
    my $line = $_[0];

    my $HEADLENP = 11;    # position of header length
    my $beg0 = 28;        # position of first atom resid
    my $end0 = 33;        # position of second atom resid

    my $headlen = substr($line,$HEADLENP,3);
    my $header = substr($line, 0, $headlen);
    my $body = substr($line, $headlen);

    my $beg = substr($line,$beg0,5);
    my $end = substr($line,$end0,5);

#    $body =~ s/^.*($beg.+$end..).*$/$1/;
#    return $body;

    # create @map to contain array of all residues in range $beg to $end,
    # trim away excess
    my @map = $body =~ /(.{7})/g;

    # remove beginning entries that don't match $beg
    while (defined $map[0] && !($map[0] =~ /$beg/)) {
	shift @map;
    }

    # remove trailing entries that don't match $end
    while (defined $map[$#map] && !($map[$#map] =~ /$end/)) {
	pop @map;
    }

    my $mapString = "";
    foreach (@map) {
	$mapString .= $_;
    }

    return $mapString . "\n";
}

=head2 printme()

 Usage   : $chain->getprintme()
 Returns : nothing
 Args    : none

 Prints a brief description of the RAF object, with information including: 
  * PDB+chain ID 
  * RAF version 
  * header length 
  * time stamp 
  * bit flags 
  * first residue ID 
  * last residue id 
  * body 
  * current residue index 

 Where each bit in "bit flags" corresponds to, from right to left, 
  * 1->mapped 
  * 2->active 
  * 3->checked 
  * 4->manually edited 
  * 5->ok 
  * 6->one-to-one mapping 

 Sample use:

  my $raf = RAF::new();
  my $chain = $raf->getChain("101m_");
  $chain->printme();

=cut

# printme : arguments : none
#           returns : nothing
# Description: prints a description of the SCOP object
sub printme {
    my $self = shift;
    printf("pdbchain = %s\n",$self->getName());
    printf("version = %s\n",$self->getVersion());
    printf("headlen = %s\n",$self->getHeaderLength());
    printf("timestamp = %s\n",$self->getTimeStamp());
    printf("bitflags = %s\n",$self->getBitFlags());
    printf("firstResID = %s\n",$self->getFirstResID());
    printf("lastResID = %s\n",$self->getLastResID());
    printf("body = %s",$self->getBody());
    printf("currentRes index = %d\n",$self->getCurrentRes());
}

# printme : arguments : none
#           returns : nothing
# Description: prints a description of the SCOP object in HTML format
sub printmehtml {
    my $self = shift;
    printf("PDB chain  : %s<br>\n",$self->getName());
    printf("Body       : %s<br>\n",$self->getBody());
    printf("ATOMSEQ    : %s<br>\n",$self->getSeq(1));
    printf("SEQRES     : %s<br>\n",$self->getSeq(0));
}


################################################################################
########                    CHAIN FUNCTIONS                           ##########
################################################################################

=head2 getRes()

 Usage   : $chain->getRes(int $resNum, int $seqOption)
 Returns : one-letter residue symbol
 Args    : $resNum    : residue number, starting from 0
           $seqOption : 0 for seqres, 1 for atomres, 2 for resID

 Returns the SEQRES residue, ATOM record residue, or residue ID given by
 $resNum. -1 is returned if no such residue is found (i.e., if $resNum is out
 of the range of this chain). 

 $resNum counts from 0, so the first residue is 0, the second is 1, etc. 

 $seqOption can take three different values: 
  * 0 to return SEQRES residue 
  * 1 to return ATOMRES residue
  * 2 to return residue ID 

 Note: Since "." is a legal character in the RAF, it is also a possible return
 value from getRes(). 

 Sample use:

  my $raf = RAF::new();

  # get the hemoglobin chain
  my $chain = $raf->getChain("101m_");

  my $seq6   = $chain->getRes(5,1);    # get the 6th SEQRES residue
  my $atm21  = $chain->getRes(20,0);   # get the 21st ATOMRES residue
  my $resid3 = $chain->getRes(2,2);    # get the 3rd residue ID

=cut

# getRes : arguments : $_[0] : residue number
#                      $_[1] : 0 for seqres, 1 for atomres, 2 for resID
#          returns : one-letter residue symbol or ".", or the number -1 if
#                    the residue number is out of bounds
# Notes: first residue is number 0, second is 1, etc...regardless of the
#        actual format residue number
sub getRes {
    my $self = shift;

    if ((!defined $_[0]) || (!defined $_[1])) {
	die "invalid arguments in getRes, $!\n";
    }

    my $maxResNum = $self->nRes() - 1;

    # return 0 if out of bounds
    if (($_[0] < 0) || ($_[0] > $maxResNum)) {
	return -1;
    }

    my $body = $self->getBody();
    my @map = $body =~ /(.{7})/g;

    $portion = $map[$_[0]];

    # return atomres value
    if ($_[1] == 1) {
	return substr($portion,5,1);

    #return seqres value
    } elsif ($_[1] == 0) {
	return substr($portion,6,1);

    } elsif ($_[1] == 2) {
	my ($resID) = (substr($portion,0,5) =~ /\s*(\S+)/);
	return $resID;
    } else {
	die "second argument to getRes must be 0, 1, or 2.$!\n";
    }
}

=head2 getNextRes()

 Usage   : $chain->getNextRes($seqOption)
 Returns : one-letter residue symbol
 Args    : $seqOption : 0 for seqres, 1 for atomres, 2 for resID

 Returns the SEQRES residue, ATOM record residue, or residue ID given by the
 internal current residue counter of this chain. The residue counter is incremented in
 the process. -1 is returned if there are no more residues. 

 $seqOption can take three different values: 
  * 0 to return SEQRES sequence 
  * 1 to return ATOMRES sequence 
  * 2 to return residue ID sequence 

 Notes: 
  * The current residue number is initially set to 0. 
  * The function resetCurrentRes() resets the current residue counter to 0. 
  * The current value of the counter can be retreived with getCurrentRes(). 
  * Since "." is a legal character in the RAF, it is also a possible return
    value from getNextRes(). 

 Sample use:

  my $raf = RAF::new();

  # get the hemoglobin chain
  my $chain = $raf->getChain("101m_");

  # get the first 5 ATOMRES residues
  my $residues = "";
  for (my $i=0 ; $i<5 ; $i++) {
  $residues .= $chain->getNextRes(1);
  }

=cut

# getNextRes : arguments : $_[0] : 0 for seqres, 1 for atomres
#              returns : one-letter residue symbol or ".", or the number
#                        -1 if the current residue number is out of bounds, then
#                        advances currentRes index
sub getNextRes {
    my $self = shift;
    if ((!defined $_[0]) ||
	(($_[0] != 0) &&
	 ($_[0] != 1) &&
	 ($_[0] != 2))) {
	die "first argument to getNextRes must be 0, 1, or 2";
    }
    return $self->getRes(($self->{currentRes})++,$_[0]);
}

# comp : arguments : $_[0] : a residue ID
#                    $_[1] : another residue ID
#        returns : -1 if $_[0] is less than $_[1]
#                   0 if $_[0] is same as $_[1]
#                   1 if $_[0] is greater than $_[1]
sub comp {
    my $self = shift;
    my ($anum, $aletter) = ($_[0] =~ /\s*-?(\d*)(\w*)/);
    my ($bnum, $bletter) = ($_[1] =~ /\s*-?(\d*)(\w*)/);

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

=head2 getResAbs()

 Usage   : $chain->getResAbs(string $absRes, int $seqOption)
 Returns : one-letter residue symbol
 Args    : $resNum    : residue number
           $seqOption : 0 for seqres, 1 for atomres, 2 for resID

 Returns the SEQRES residue, ATOM record residue, or relative residue ID number
 given by the absolute residue ID $absRes. -1 is returned if no such residue is
 found (i.e., if $absRes is not in the chain).

 $resNum uses absolute labeling; that is, it counts from the first residue id
 to the last residue id of the chain.

 $seqOption can take three different values: 
 * 0 to return SEQRES residue 
 * 1 to return ATOMRESresidue 
 * 2 to return the relative residue ID number, where the first residue is 0,
   the second is 1, etc.

 Note: Since "." is a legal character in the RAF, it is also a possible return
 value from getRes(). 

 Sample use:

  my $raf = RAF::new();

  # get the Bacteriophage T4 lysozyme protein; the first few residues in the RAF are:
  # 1  162    1 mm   2 nn   3 ii   4 ff   5 ee   6 mm   7 ll   8 rr   9 ii  10 dd  11 ee
  # 12 gg 13 ll  14 rr  15 ll  16 kk  17 ii  18 yy  19 kk  20 dd  21 tt  22 ee  23 gg  24 yy 
  # 25 yy  26 tt  27 ii  28 gg  29 ii  30 gg  31 hh  32 ll  33 ll  34 tt   M .k   M .s
  # M .p   M .s   M .l   M .n  40Ass  40Bll  40Cdd  41 aa  42 aa  43 kk  44 ss  45 ee  46 ll
  # 47 dd  48 kk  49 aa  50 ii  51 gg  52 rr  53 nn  54 tt

  my $chain = $raf->getChain("103l_");

  my $seq6   = $chain->getResAbs("5",1);    # get SEQRES residue 5, or "e"
  my $atm21  = $chain->getResAbs("20",0);   # get ATOMRES residue 20, or "d"
  my $resid3 = $chain->getResAbs("40B",2);  # get relative residue number for 40B, or 41

=cut

# getResAbs : arguments : $_[0] : absolute residue ID
#                         $_[1] : 0 for seqres, 1 for atomres, 2 for relative residue ID number
#             returns : the seqres residue, atomres residue, or relative residue ID number of
#                       given in $_[0]; or -1 if no such residue found
sub getResAbs {
    my $self = shift;
    my $res = $_[0];
    my @map = $self->getBody() =~ /(.{7})/g;

    my $nRes = (scalar @map);
    my ($mapres);

    # don't assume residues are consecutive
    for (my $i=0; $i<$nRes; $i++) {
	($mapres) = (substr($map[$i],0,5) =~ /\s*(\S+)/);

	my $comp = $self->comp($res,$mapres);

	if ($comp == 0) {
	    my $portion = $map[$i];
	    if ($_[1] == 1) {
		# return atomres value
		return substr($portion,5,1);
	    } elsif ($_[1] == 0) {
		#return seqres value
		return substr($portion,6,1);
	    } elsif ($_[1] == 2) {
		return $i;
	    } else {
		die "second argument to getRes must be 0, 1, or 2,$!\n";
	    }
	}
    }

    return -1;
}

=head2 resetCurrentRes()

 Usage   : $chain->resetCurrentRes()
 Returns : nothing
 Args    : none

 Sets the current residue counter to 0. 

 Sample use:

  my $raf = RAF::new();
  my $chain = $raf->getChain("101m_");
  $chain->resetCurrentRes();

=cut

# resetCurrentRes : arguments : none
#                   returns : nothing
# Description : sets currentRes to 0
sub resetCurrentRes {
    my $self = shift;
    $self->{currentRes} = 0;
}

=head2 nRes()

 Usage   : $chain->nRes()
 Returns : number of residues
 Args    : none

 Returns the number of residues in the chain, including missing entries for
 which the residue value is ".". 

 Sample use:

  my $raf = RAF::new();
  my $chain = $raf->getChain("101m_");
  printf("There are %d residues\n", $chain->nRes());

=cut

# nRes : arguments : none
#        returns : number of residues in $_[0], not taking
#                  into account the numbering system, ie
#                  1a .. 1b .. 1c .. is considered 3 residues
sub nRes {
    my $self = shift;
    return int ((length $self->getBody()) / 7);  # 7 chars per residue entry
}

=head2 nMissing()

 Usage   : $chain->nMissing()
 Returns : number of missing residues
 Args    : none

 Returns the number of missing residues in the chain -- the number of residues
 with residue id equal to "M".

 Sample use:

  my $raf = RAF::new();
  my $chain = $raf->getChain("101m_");
  printf("There are %d missing residues\n", $chain->nMissing());

=cut

# nMissing : arguments : none
#            returns : number of missing residues in $_[0] starting with "M"
sub nMissing {
    my $self = shift;
    my @map = $self->getBody() =~ /(.{7})/g;
    my $missingCount = 0;
    my $map;
    foreach (@map) {
	# if missing
	$_ =~ s/^\s*//;

	if (substr($_,0,1) eq "M") {
	    $missingCount++;
	}
    }
    return $missingCount;
}

=head2 getMap()

 Usage   : $chain->getMap(string $range, int $seqOption, int $missOption)
 Returns : string corresponding to seqres, atomres, or residue IDs
 Args    : $range      : extract chain data from this $range, using relative
                         numbering
         : $seqOption  : 0 for seqres, 1 for atomres, 2 for resID
         : $missOption : 0 or undef to exclude missing residues, 1 to include

 Returns the SEQRES, ATOM record, or residue ID sequences in the given $range. 
 $range is a string of the form "$start-$end", where $start is the first residue
 and $end is the last. Residue 0 is the first residue. 

 If $start is ommited, 0 is assummed; if $end is ommitted, the final residue is
 assummed. 

 $seqOption can take three different values: 
  * 0 to return SEQRES sequence 
  * 1 to return ATOMRES sequence 
  * 2 to return residue ID sequence 

 $missOption specifies whether or not to include missing residues, and can take two forms: 
  * undefined or 0 to exclude missing residues of the form "." 
  * 1 to include the missing residues 

 Sample use:

  my $raf = RAF::new();

  # get the hemoglobin chain
  my $chain = $raf->getChain("101m_");

  my $seq = $chain->getMap("1-20",0,1);  # get SEQRES residues 2 to 21, include "." residues
  my $atm = $chain->getMap("51-",1,0);   # get ATOMRES residues 52 to end, exclude "." residues
  my $res = $chain->getMap("-",2);       # get residue IDs for all residues, exclude "." residues

=cut

# getMap : arguments : $_[0] : string containing residue range, ie:
#                                * "1-20" -- residues 1 through 20
#                                * "-20" -- residues 0 through 20
#                                * "1-" -- residues 1 through last residue
#                              Where the numbers correspond to the relative residue numbers,
#                              counting from 0
#                      $_[1] : 0 for seqres, 1 for atomres, 2 for resID
#                      $_[2] : 0 or undef to exclude missing residues, 1 to include
#          returns : * string corresponding to seqres, atomres, or ResID
# Dies if invalid arguments
sub getMap {
    my $self = shift;

    if ($_[0] =~ /^(\S+?)-(\S+)/) {
	($beg,$end) = ($_[0] =~ /^(\S+?)-(\S+)/);
    }
    else {
	($beg,$end) = ($_[0] =~ /^(\S*)-(\S*)/);
    }

    if ((!defined $beg) || ($beg eq "")) {
	$beg = 0;
    }
    if ((!defined $end) || ($end eq "")) {
	$end = $self->nRes() - 1;
    }

#    if (($beg < 0) || ($end >= $self->nRes) || ($end < $beg)) {
#        die "invalid arguments to getMap\n";
#    }

    return $self->getRes($beg,$_[1]) if ($beg == $end);

    my ($ok,$seqres, $atomres, $resid);
    if ((!defined $_[2]) || ($_[2] == 0)) {
	$end -= $self->nMissing();
	($ok,$seqres, $atomres, $resid) = $self->rafseqHelper(undef, undef, 0);
    } else {
	($ok,$seqres, $atomres, $resid) = $self->rafseqHelper(undef, undef, 1);
    }

    if ($_[1] == 0) {
        return substr($seqres,$beg,($end-$beg)+1);
    } elsif ($_[1] == 1) {
        return substr($atomres,$beg,($end-$beg)+1);
    } else {
	my @resid = split(/ +/,$resid);

	my $i;
	$resid = "";
	for ($i=$beg ; $i<=$end ; $i++) {
#	    print("i = $i\n");
	    $resid .= $resid[$i] . " " if (defined $resid[$i]);
	}

#	exit(0);
        return $resid;
    }
}

=head2 getMapAbs()

 Usage   : $chain->getMapAbs(string $range, int $seqOption, int $missOption)
 Returns : string corresponding to seqres, atomres, or residue IDs
 Args    : $range      : extract chain data from this $range, using absolute
                         numbering
         : $seqOption  : 0 for seqres, 1 for atomres, 2 for resID
         : $missOption : 0 or undef to exclude missing residues, 1 to include

 Returns the SEQRES, ATOM record, or residue ID sequences in the given $range. 

 $range is a string of the form "$start-$end", where $start is the first residue
 and $end is the last. Unlike getMap, getMapAbs uses absolute addressing, not
 relative addressing. 

 If $start is ommited, the first residue is assummed; if $end is ommitted, the
 final residue is assummed. 

 $seqOption can take three different values: 
  * 0 to return SEQRES sequence 
  * 1 to return ATOMRES sequence 
  * 2 to return relative residue number sequence 

 $missOption specifies whether or not to include missing residues, and can take
 two forms: 
  * undefined or 0 to exclude missing residues of the form "." 
  * 1 to include the missing residues 

 Sample use:

  my $raf = RAF::new();

  # get the Bacteriophage T4 lysozyme
  my $chain = $raf->getChain("103l_");

  my $seq = $chain->getMapAbs("1-20",0,1);  # get SEQRES residues 1 to 20, include "." residues
  my $atm = $chain->getMapAbs("51-",1,0);  # get ATOMRES residues 51 to end, exclude "." residues
  my $res = $chain->getMapAbs("-",2);    # get residue IDs for all residues, exclude "." residues

=cut

# getMapAbs : arguments : $_[0] : string containing absolute residue range, ie:
#                                 * "1-20" -- absolute residues 1 through 20
#                                 * "-20" -- absolute residues 0 through 20
#                                 * "1-" -- absolute residues 1 through last residue
#                              Where the numbers correspond to the absolute residue numbers
#                      $_[1] : 0 for seqres, 1 for atomres, 2 for relative resID
#                      $_[2] : 0 or undef to exclude missing residues, 1 to include
#          returns : * string corresponding to seqres, atomres, or ResID
# Dies if invalid arguments
sub getMapAbs {
    my $self = shift;

    my ($beg,$end);

    if ($_[0] =~ /^(\S+?)-(\S+)/) {
	($beg,$end) = ($_[0] =~ /^(\S+?)-(\S+)/);
    }
    else {
	($beg,$end) = ($_[0] =~ /^(\S*)-(\S*)/);
    }

    if ((!defined $beg) || ($beg eq "")) {
        $beg = $self->getFirstResID();
    }
    if ((!defined $end) || ($end eq "")) {
        ($end) = ($self->getLastResID());
    }

#   if (($self->comp($beg,$self->getFirstResID()) == -1) || 
#	($self->comp($end,$self->getLastResID()) == 1) || 
#	($self->comp($end,$beg) == -1)) {
#	print("\n\n");
#	$self->printme();
#        die "invalid arguments to getMap: $_[0]\n";
#    }

    if ($self->comp($beg,$end) == 0) {
	if ($_[1] < 2) {
	    return $self->getResAbs($beg,$_[1]);
	}
	else {
	    return $beg;
	}
    }

    my ($ok, $seqres, $atomres, $resid);
    if ((!defined $_[2]) || ($_[2] == 0)) {
        ($ok, $seqres, $atomres, $resid) = $self->rafseqHelper($beg, $end, 0, 0); # last 1 for relative addresses
    } else {
        ($ok, $seqres, $atomres, $resid) = $self->rafseqHelper($beg, $end, 1, 0); # last 1 for relative addresses
    }

    if ($_[1] == 0) {
        return $seqres;
    } elsif ($_[1] == 1) {
        return $atomres;
    } else { # ignore res, reconstruct it
	return $resid;
    }
}

# nSeqsLocal : arguments : $_[0] : string like "T:,U:91-106"
#              returns : number of unique chains in string
sub nSeqsLocal {
    my @id = split(/,/,$_[0]);
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

=head2 getSeq()

 Usage   : $chain->getSeq(int $seqOption)
 Returns : string corresponding to seqres, atomres, or residue IDs
 Args    : $seqOption  : 0 for seqres, 1 for atomres, 2 for resID

 Returns the SEQRES, ATOM record, or residue ID sequence, depending on
 the value of $seqOption.

 $seqOption can take three different values:
  * 0 to return SEQRES sequence 
  * 1 to return ATOMRES sequence 
  * 2 to return residue ID sequence 

 Sample use:

  my $raf = RAF::new();

  # get the hemoglobin chain
  my $chain = $raf->getChain("101m_");

  my $seq = $chain->getSeq(0);    # get entire SEQRES sequence
  my $atm = $chain->getSeq(1);    # get entire ATOMRES sequence
  my $resid = $chain->getSeq(2);  # get entire residue ID sequence

=cut

# getSeq : arguments : $_[0] : 0 if seqres, 1 if atomres, 2 if resID
#          returns : string containing sequence, ommitting unknown residues (ie ".")
sub getSeq {
    my $self = shift;
    my ($seqOption) = @_;
    my ($seqres, $atomres, $beg, $end, $map) = ();

    undef $beg; undef $end;

    ($ok, $seqres, $atomres, $resid) = $self->rafseqHelper($beg, $end, 1, 0, 1);

    if ($seqOption == 0) {
	$seqres =~ s/\.//g;
        return $seqres;
    } elsif ($seqOption == 1) {
	$atomres =~ s/\.//g;
	return $atomres;
    } elsif ($seqOption == 2) {
	return $resid;
    } else {
	die "second argument must be 0, 1, or 2,$!\n";
    }
}


################################################################################
########                 SEQUENCING HELPER FUNCTIONS                  ##########
################################################################################

# rafseqHelper : arguments : $_[0] : first atom in $map, undef for first residue
#                            $_[1] : last atom in $map, undef for last residue
#                            $_[2] : 0 or undef to exclude ".", else include(opt)
#                            $_[3] : 0 or undef for absolute, else relative
#                            $_[4] : 0 or undef to select ATOM delimited residues (default),
#                                    otherwise select entire chain
#                returns three values :
#                            1) 1 if completed successfully, 0 otherwise
#                            2) seqres sequence in string format
#                            3) atomres sequence in string format
# Description: Isolates and returns the seqres/atomres sequences. If $_[2] is 0
#              or undefined then missing residues "." are not included;
#              otherwise, they are included.
sub rafseqHelper {
    my $self = shift;
    my ($beg, $end, $missing) = @_;

    my $HEADLENP = 11;    # position of header length
    my $beg0 = 28;        # position of first atom resid
    my $end0 = 33;        # position of second atom resid

    my $body = $self->{body};
    my @map = $body =~ /(.{7})/g;

    if ((defined $beg) && (defined $end)) {
        $beg = ResidFormat (uc $beg);
        $end = ResidFormat (uc $end);
    } else {  # fill get $beg and $end depending on $_[4]
	if (!defined $_[4] || !$_[4]) {
	    # ATOM delimited
	    $beg = ResidFormat (uc $self->getFirstResID());
	    $end = ResidFormat (uc $self->getLastResID());
	} else {
	    # entire chain
	    $beg = $map[0];                #$self->{firstResID};
	    $end = $map[$#map];            #$self->{lastResID};
	}
    }

    return 0 if ! (($body =~ /$beg/) && ($body =~ /$end/));

    # create @map to contain array of all residues in range $beg to $end,
    # trim away excess
#    my @map = $body =~ /(.{7})/g;

    # remove beginning entries that don't match $beg
    while (defined $map[0] && !($map[0] =~ /$beg/)) {
	shift @map;
    }

    # remove trailing entries that don't match $end
    while (defined $map[$#map] && !($map[$#map] =~ /$end/)) {
	pop @map;
    }

    return 0 if !defined $map[0];

    my $fragSeqres = "";
    my $fragAtomres = "";
    my $resid = "";
    my $resRel = "";
    my ($s0, $s1, $s2);
    my $count = 0;

    foreach (@map) {
        $s0 = substr($_,5,1); $s1 = substr($_,6,1);
	($s2) = (substr($_,0,5) =~ /\s*(\S+)/);

	if ((!defined $_[2]) || ($_[2] == 0)) { # remove missing residues
          $fragAtomres .= "$s0"     if ($s2 ne 'B' && $s2 ne 'M' && $s2 ne 'E');
          $fragSeqres  .= "$s1"     if ($s2 ne 'B' && $s2 ne 'M' && $s2 ne 'E');
          $resid       .= "$s2 "    if ($s2 ne 'B' && $s2 ne 'M' && $s2 ne 'E');
          $resRel      .= "$count " if ($s2 ne 'B' && $s2 ne 'M' && $s2 ne 'E');
	} elsif ($_[2] == 1) {
	    $fragAtomres .= "$s0";
            $fragSeqres .= "$s1";
	    $resid .= "$s2 ";
	    $resRel .= "$count ";

	} else {
	    die "third argument to rafseqHelper must be 0, 1, or undefined but is $_[2], $!";
	}

	$count++;
    }
    return (1,$fragSeqres,$fragAtomres, ((!defined $_[3]) || ($_[3] == 0)) ? $resid : $resRel);
}

# ResidFormat : used by rafseqHelper
sub ResidFormat {
    $_ = shift;
    my ($d,$a) = $_ =~ /^(-?\d+)([A-Za-z])?/;
    $a = " " if !defined $a;
    return " " x (4 - length $d) . $d . $a;
}


################################################################################
########                    MATCHING FUNCTIONS                        ##########
################################################################################

# compare : arguments $_[0] : kind of comparision (0 for hasMissingAtomres,
#                                                  1 for hasMissingSeqres,
#                                                  2 for match)
#                     $_[1] : range in for "$beg-$end"
#           returns : 0 or 1, depending on the kind of operation;
#                     see hasMissingAtomres, hasMissingSeqres, match
sub compare {
    my $self = shift;

    my $seqres = $self->getMap($_[1], 0, 1);
    my $atomres = $self->getMap($_[1], 1, 1);

    print("a = $atomres\ns = $seqres\n");

    # hasMissingAtomres
    if ($_[0] == 0) {
	return $atomres =~ /\./;

    # hasMissingSeqres
    } elsif ($_[0] == 1) {
	return $seqres =~/\./;

    # match
    } elsif ($_[0] == 2) {
	($atomres eq $seqres) ? return 1 : return 0;
    } else {
	die "invalid first argument, must be 0, 1 or 2,$!\n";
    }
}

=head2 hasMissingAtomres()

 Usage   : $chain->hasMissingAtomres(string $range)
 Returns : 0 or 1
 Args    : none

 Returns 1 if the ATOMRES sequence in the $range given has fewer residues than
 the SEQRES sequence, 0 otherwise. Missing residues (".") are ignored.

 The $range argument is optional, defaulting to the entire range. When
 given, the values in $range count from 0 -- that is, the first residue is
 residue 0. The $range argument has four different formats: 
  * "$start-$end", begin the range from $start and stop at $end. 
  * "$start-", specifies range from $start to the last residue of the chain. 
  * "-$end", from the first residue to $end. 
  * "-", entire range of chain (same as not giving the range argument) 

 Sample use:

  my $raf = RAF::new();
  my $chain = $raf->getChain("101m_");
  printf("There %s missing ATOMRES residues\n",($chain->hasMissingAtomres("-")) ? "are" : "are no");

=cut

# hasMissingAtomres : arguments $_[0] : range in the form "$beg-$end", where
#                     either may be ommitted and 0 is first residue in chain
#                     returns : 1 if length of atomres < length of seqres,
sub hasMissingAtomres {
    my $self = shift;
    $self->compare(0,$_[0]);
}

=head2 hasMissingSeqres()

 Usage   : $chain->getSeq(string $range)
 Returns : 0 or 1
 Args    : none

 Returns 1 if the ATOMRES sequence in the $range given has more residues than
 the SEQRES sequence, 0 otherwise. Missing residues (".") are ignored.

 The $range argument is optional, defaulting to the entire range. When
 given, the values in $range count from 0 -- that is, the first residue is
 residue 0. The $range argument has four different formats: 
  * "$start-$end", begin the range from $start and stop at $end. 
  * "$start-", specifies range from $start to the last residue of the chain. 
  * "-$end", from the first residue to $end. 
  * "-", entire range of chain (same as not giving the range argument) 

 Sample use:

  my $raf = RAF::new();
  my $chain = $raf->getChain("101m_");
  printf("There %s missing SEQRES residues\n",($chain->hasMissingSeqres("-")) ? "are" : "are no");

=cut

# hasMissingSeqres : arguments $_[0] : range in the form "$beg-$end", where
#                    either may be ommitted and 0 is first residue in chain
#                    returns : 1 if length of atomres > length of seqres
sub hasMissingSeqres {
    my $self = shift;
    $self->compare(1,$_[0]);
}

=head2 match()

 Usage   : $chain->match(string $range)
 Returns : 0 or 1
 Args    : none

 Returns 1 if the ATOMRES sequence in the $range given matches the SEQRES
 sequence, 0 otherwise. Missing residues (".") are ignored. 

 The $range argument is optional, defaulting to the entire range. When
 given, the values in $range count from 0 -- that is, the first residue is
 residue 0. The $range argument has four different formats: 
  * "$start-$end", begin the range from $start and stop at $end. 
  * "$start-", specifies range from $start to the last residue of the chain. 
  * "-$end", from the first residue to $end. 
  * "-", entire range of chain (same as not giving the range argument) 

 Sample use:

  my $raf = RAF::new();
  my $chain = $raf->getChain("101m_");
  printf("SEQRES and ATOMRES sequences %s\n", ($chain->match("3-8")) ? "match" : "don't match");

=cut

# match : arguments : $_[0] : range in the form "$beg-$end", where
#                     either may be ommitted and 0 is first residue in chain
#         returns : 1 if atomres eq seqres
sub match {
    my $self = shift;
    $self->compare(2,$_[0]);
}


################################################################################
########                    SEMANTIC FUNCTIONS                        ##########
################################################################################

# getMismatch : arguments : $_[0] : beginning residue in absolute numbering (*)
#                           $_[1] : ending residue in absolute numbering (*)
#               (*) optional, defaulting to entire chain
#               returns : undef if SEQRES matches ATOMRES;
#                         a string if SEQRES does not match ATOMRES;
#                         this string is of the form:
#                            <pdb chain> <SEQRES residue> <res ID> <ATOMRES residue>
#                         if there are multiple such strings, they are concatenated
#                           and separated by newlines
#                         if an ATOM residue is missing, we ignore it in mismatch
sub getMismatch {
    my $self = shift;
    my $mismatchString = "";

    my @map = $self->getBody() =~ /(.{7})/g;
    if (defined $_[0]) {
	while ($map[0] =~ /^\s*$_[0]\s*/) {
	    shift @map;
	}
    }
    if (defined $_[1]) {
	while ($map[$#map] =~ /^\s*$_[1]\s*/) {
	    pop @map;
	}
    }

    foreach (@map) {
	my ($resid, $atomres, $seqres) = ($_ =~ /^\s*(\S+)\s*(\S)(\S)\s*$/);
	next if ($atomres eq ".");       # skip missing

	if (($atomres ne '.') &&
	    ($seqres ne '.') &&
	    ($seqres ne $atomres)) {
	    $mismatchString .= "MISMATCH: " . $self->getName() . " " . $seqres . " " .
		               $resid . " -> " . $atomres . "\n";
	}
    }

    return ($mismatchString eq "") ? undef : $mismatchString;
}

# hasMismatch : arguments : none
#               returns : 1 if chain has mismatches, 0 otherwise
#                         if an ATOM residue is missing, we ignore it in mismatch
sub hasMismatch {
    my $self = shift;

    my @map = $self->getBody() =~ /(.{7})/g;
    if (defined $_[0]) {
        while ($map[0] =~ /^\s*$_[0]\s*/) {
            shift @map;
        }
    }
    if (defined $_[1]) {
        while ($map[$#map] =~ /^\s*$_[1]\s*/) {
            pop @map;
        }
    }

    foreach (@map) {
        my ($resid, $atomres, $seqres) = ($_ =~ /^\s*(\S+)\s*(\S)(\S)\s*$/);
        next if ($atomres eq ".");       # skip missing
        return 1 if ($seqres ne $atomres);
    }

    return 0;
}

# checkSyntax : arguments : none
#               returns : undef if no syntax errors found in chain;
#                         a string containing error information otherwise
sub checkSyntax {
    my $self = shift;
    my $errorString = "";

    # check isChecked and isOk flags
    if (!$self->isChecked()) {
	$errorString .=  "NOTCHECKED: " . $self->getName() . "\n";
    }
    if (!$self->isOk()) {
	$errorString .=  "NOTOK: " . $self->getName() . "\n";
    }

    # check body length
    my $body = $self->getBody();
    chomp($body);
    my $blength = length($body);
    my $n = $self->nRes();
    if (($n != $blength/7) || (($blength % 7) != 0)) {
	$errorString .= "SYNTAX: " . $self->getName() . ", res=$n, body length=$blength\n";
#	$errorString .= "body = '" . $body . "'\n";
	return $errorString;        # big error, return immediately
    }

    # check for bad res ids
    my $fr1 = $self->getFirstResID();
    my $lr1 = $self->getLastResID();
    my $fr2;
    my $lr2;
    my @map = $self->getBody() =~ /(.{7})/g;
    my ($resid, $atomres, $seqres);
    my $last_resid;
    my ($badatom, $badseq) = (0,0);
    my %seen;
    foreach (@map) {
	($resid, $atomres, $seqres) = ($_ =~ /^\s*(\S+)\s*(\S)(\S)\s*$/);

	if ( (not defined $resid) || (not defined $atomres) || (not defined $seqres) ) {
	    $errorString .= "SYNTAX: " . $self->getName() . "\n";
	    return $errorString;    # big error, return immediately
	}

	if (($resid ne "B") &&
	    ($resid ne "M") &&
	    ($resid ne "E")) {
	    if (exists($seen{$resid})) {
		$errorString .= "DUPE: " . $self->getName() . " " . $resid . "\n";
	    }
	    else {
		$seen{$resid} = 1;
		if (defined($last_resid) &&
		    (int($last_resid) > int($resid))) {
		    $errorString .= "NONCONSEC: " . $self->getName() . " " . $last_resid . " " . $resid . "\n";
		}
	    }
	    $last_resid = $resid;
	}


	# look for missing residues
	if ( ($atomres eq ".") ){
	    $badatom++;
	}
	if ( ($seqres eq ".") ) {
	    $badseq++;
	}

	$fr2 = $resid if ((! defined $fr2) && ($resid ne "B"));    # get the real first resid
	$lr2 = $resid if ($resid ne "E");                          # get the real last id
    }
    if ($fr1 ne $fr2) {
	$errorString .= "FIRST: " . $self->getName() . "\n";
    }
    if ($lr1 ne $lr2) {
	$errorString .= "LAST: " . $self->getName() . "\n";
    }
    $errorString .= "MISSINGATOM: " . $self->getName() . "\n" if ($badatom);
    $errorString .= "MISSINGSEQ: " . $self->getName() . "\n" if ($badseq);

    # check for mismatches
    my $mismatchString = $self->getMismatch();
    $errorString .= (defined $mismatchString) ? $mismatchString : "";

    return ($errorString eq "") ? undef : $errorString;
}

1;
