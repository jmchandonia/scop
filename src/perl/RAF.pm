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
# 0.2 - Fixed some comments that gave global-font-lock trouble. (Gary 10/29/02)
# 0.1 - Initial version

=head1 NAME

    RAF.pm - Rapid Access Format (RAF) module.

=head1 SYNOPSIS

    Perl module

=head1 DESCRIPTION

The RAF class is responsible for the organization of PDBChain objects. A RAF
object can be created by calling the RAF constructor new(), for example

    my $raf = new("astal.txt","index.idx");

More details can be found in the constructor section. From this, the RAF object
can create an index hash to facilitate fast lookup of entries in the RAF
mapping. The RAF object can then be queried for PDBChain objects: 

    my $chain = $raf->getChain("101m_");    # get hemoglobin chain

Among other things, RAF objects can also be interrogated for:

 * a list of chain objects whose PDB+chain IDs match a given pattern
   (getChains()) 
 * a list of all chains described in the object's respective RAF mapping
   (getAllChains()) 
 * A more comprehensive list of methods can be foud in the method summary.

The idea is to have multiple RAF objects, each with different RAF mapping and
index files. This makes tracking the evolution of chain objects through
different versions of the RAF relatively easy. Let's see how hemoglobin has
changed: 

    my $oldRAF = new("astral.old","index.old");
    my $newRAF = new("astral.new","index.new");

    my $oldchain = $oldRAF->getChain("101m_");
    my $newchain = $newRAF->getChain("101m_");

    printf("the chains are %s\n", ($oldchain->getSeq(1) eq $newchain->getSeq(1))
	   ? "same" : "different");

=cut

package RAF;

use FileHandle;
use CDB_File;
use PDBChain;

################################################################################
########              PRIVATE STATE FOR EACH OBJECT                   ##########
################################################################################

#  * RAFmapFile              mapping file in RAF format
#  * indexFile               index file for $RAFmapFile
#  * logFile                 log file
#  * idx                     local copy of index hash
#
# For creation of index file:
#  * indexSkip               skip line if it starts with $indexSkip
#                              (ie comment char)
#  * indexKeyStart           key starts at char $indexKeyStart (counting from 0)
#  * indexKeyLength          key length
#

=head1 APPENDIX

=cut

################################################################################
########                      CONSTRUCTORS                            ##########
################################################################################

=head2 new()

 Usage   : my $raf = RAF::new()
         : my $raf = RAF::new($RAFmapFile)
         : my $raf = RAF::new($RAFmapFile, $indexFile)
         : my $raf = RAF::new (undef, undef, string $comment, int $keyStart,
			       int $keyLength)
         : my $raf = RAF::new(string $RAFmapFile, undef, string $comment,
			      int $keyStart, int $keyLength)
         : my $raf = RAF::new(string $RAFmapFile, string $indexFile,
			      string $comment, int $keyStart, int $keyLength)
 Returns : new RAF object
 Args    : $RAFmapFile : a RAF file (ie astral-rapid-access-1.55.raf)
         : $indexFile  : an index file (ie index.idx)
         : $comment    : index comment character
         : $keyStart   : index key start
         : $keyLength  : index key length

 Constructs a new RAF object using the filenames given, with the index hash
 altered by the last three arguments.

 Lines in $RAFmapFile will be ignored if they begin with $comment. $keyStart
 characters are skipped per RAF map line before a key is recorded, of length
 $keyLength. 

 Note that the index creation variables ($comment, $keyStart, $keyLength) must
 all be specified or all not specified. $keyStart and $keyLength can have the
 value "-1" for no  effect. The default value for $comment is "#". 

 The $RAFmapFile must exist or be undef. If undef, the default $RAFmapFile will
 be used. The index is saved under $indexFile; it too can be undef, in which
 case the default will be used.

 Sample use:

  my $raf = RAF::new();
  my $raf = RAF::new("astral-rapid-access-1.55.raf");
  my $raf = RAF::new("astral-rapid-access-1.55.raf","index.idx");
  my $raf = RAF::new(undef,undef,"#",1,5);  # comment is "#", skip 1 character
                                           # and record 5 for key
  my $raf = RAF::new("astral-rapid-access-1.55.raf","#",1,5);
  my $raf = RAF::new("astral-rapid-access-1.55.raf","index.idx","#",1,5);

=cut

# new: arguments: $_[0] : a raf file (ie astral-rapid-access-1.55.raf)    (opt)
#                 $_[1] : an index file (ie index.idx)                    (opt)
#                 $_[2] : index comment character                         (opt)
#                 $_[3] : index key start                                 (opt)
#                 $_[4] : index key length                                (opt)
#      returns: a new RAF object
# First 2 arguments are optional. If none specified, use defaults.
#                                 If only first specified, we create the index
#                                   hash from the second argument.
#                                 If all specified, we use them.
# Last 3 arguments must either all be specified or all not specified.
# Use undef values if need be.
sub new {
    my ($j,$k);
    my $self;

    #default values
    $self->{RAFmapFile} = "astral-rapid-access-1.55.raf";
    $self->{indexFile} = "index.idx";
    $self->{logFile} = "/dev/null";
    $self->{idx} = {};
    $self->{indexSkip} = "#";
    $self->{indexKeyStart} = -1;
    $self->{indexKeyLength} = -1;

    bless ($self);

    # set all index values
    if (defined $_[2]) {
	$self->setIndexComment($_[2]);
	$self->setIndexKeyStart($_[3]);
	$self->setIndexKeyLength($_[4]);
    }

    # if all first 2 defined, set defaults and proceed
    if (($j = defined $_[0]) && ($k = defined $_[1])) {

        # new index creation method, so remove current index file
        if ((defined $_[2]) && ($_[1] =~ /.+\.idx$/) && (-e $_[1])) {
            system("rm -f $self->{indexFile}");
        }

	$self->setRAFFile($_[0]);
	$self->setIndexFileLocal($_[1]);
    }

    # if first defined, set defaults and make index
    elsif ($j && !$k) {

	# new index creation method, so remove current index file
        if ((defined $_[2]) && (-e $self->{indexFile})) {
            system("rm -f $self->{indexFile}");
        }

	$self->setRAFFile($_[0]);
	$self->setIndexFileLocal($self->{indexFile});
    }

    # if none defined, then use defaults to make index
    elsif (!$j && !$k) {

        # new index creation method, so remove current index file
        if ((defined $_[2]) && (-e $self->{indexFile})) {
            system("rm -f $self->{indexFile}");
        }

	$self->setRAFFile($self->{RAFmapFile});
        $self->setIndexFileLocal($self->{indexFile});
    }

    else {
	die "Invalid arguments to raf::init()\n";
    }

    return $self;
}

=head2 cleanup()

 Usage   : $raf->cleanup()
 Returns : nothing
 Args    : none

 Performs the necassary actions to properly dispose of RAF object, such as
 untying the index hash. For neater code, this should be called on RAF objects
 when they are no longer in use.

 Sample use:
  my $raf = RAF::new();

 # do some calculations
 :
 :

 # done using the RAF object, so free up resources by running cleanup()
 $raf->cleanup();

=cut

# cleanup : arguments : none
#          returns : nothing
# Description: does housekeeping of RAF module, like untying %idx
sub cleanup {
    my $self = shift;
    untie %{ $self->{idx} } if ( %{ $self->{idx} });
    undef $self->{idx};
}

# logmsg
sub logmsg {
    print LOG @_;
}


################################################################################
########                  MODIFIERS                                   ##########
################################################################################

# setIndexFileLocal : arguments : $_[0] : new name for index file
#                     returns: nothing
# if index currently tied, write it to existing index file and untie it ;
# if file exists, tie it, otherwise make a new index hash
sub setIndexFileLocal {
    my $self = shift;
    my $newfile = $_[0];

#    # if %idx currently tied, write it to existing index file and untie it
#    if (defined $self->{idx}) {
#        $self->makeIndexFile($self->{indexFile});
#        untie %{ $self->{idx} };
#    }

    # if file exists, tie it, otherwise make a new index hash
    if (-e $newfile) {
        $self->tieIndexHash($newfile);
    } else {
        $self->{idx} = $self->makeIndex();
        $self->makeIndexFile($newfile);
    }
    $self->{indexFile} = $newfile;
}

=head2 setIndexFile(string $indexFile)

 Usage   : $raf->setIndexFile($newIndexFile)
 Returns : nothing
 Args    : new index file

 The current contents of the index hash is saved. A new index hash
 is recalculated and its contents is saved in $indexFile. setIndexFile() must be
 called for alterations to the index hash creation algorithm to take effect (ie
 from calls to setIndexComment(), setIndexKeyStart, etc.). 

 Sample use:

  my $raf = RAF::new();

  # do some computations
  :
  :

  # let's alter the RAF object's parameters...
  $raf->setRAFFile("newRAF");
  $raf->setIndexComment("##");

  # but the new parameters won't take effect until it has been reset
  $raf->setIndexFile("index.new");

  # do some more computations with new RAF object with new parameters
  :
  :

=cut

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
#    if (-e $newfile) {
#	$self->tieIndexHash($newfile);
#    } else {
	$self->{idx} = $self->makeIndex();
	$self->makeIndexFile($newfile);
#    }
    $self->{indexFile} = $newfile;
}

=head2 setIndexComment(string $comment)

 Usage   : $raf->setIndexComment($comment)
 Returns : nothing
 Args    : value for comment field

 Sets $comment to be the character or sequence of characters that indicates a
 comment in the RAF map file. Note: This change is not reflected in the RAF
 object until after setIndexFile() is called. 

 Sample use:

  my $raf = RAF::new();
  $raf->setIndexComment("##");

=cut

# setIndexComment : arguments $_[0] : new value for index comment character
#                   returns : nothing
sub setIndexComment {
    my $self = shift;
    $self->{indexSkip} = $_[0] if (defined $_[0]);
}

=head2 setIndexKeyStart(int $keyStart)

 Usage   : $raf->setIndexKeyStart($keyStart)
 Returns : nothing
 Args    : number of characters to skip before counting key

 Sets $keyStart to be the number of characters to skip in a RAF line before
 counting the key. Note: This change is not reflected in the RAF object until
 after setIndexFile() is called. 

 Sample use:

  my $raf = RAF::new();
  $raf->setIndexKeyStart(1);  # skip the first character

=cut

# setIndexKeyStart : arguments $_[0] : new value for index key start,
#                                      -1 to ignore
#                    returns : nothing
sub setIndexKeyStart {
    my $self = shift;
    $self->{indexKeyStart} = $_[0] if (defined $_[0]);
}

=head2 setIndexKeyLength(int $keyLength)

 Usage   : $raf->setIndexKeyLength($keyLength)
 Returns : nothing
 Args    : length of key

 Sets the key size in index hash creation to $keyLength. Note: This change is
 not reflected in the RAF object until after setIndexFile() is called. 

 Sample use:

  my $raf = RAF::new();
  $raf->setKeyLength(5);

=cut

# setIndexKeyLength : arguments $_[0] : new value for index key length,
#                                       -1 to ignore
#                     returns : nothing
sub setIndexKeyLength {
    my $self = shift;
    $self->{indexKeyLength} = $_[0] if (defined $_[0]);
}

=head2 setLogFile(string $logFile)

 Usage   : $raf->setLogFile($logFile)
 Returns : nothing
 Args    : log file

 Sets the log file name to $logFile. 
 Sample use:

  my $raf = RAF::new();
  $raf->setLogFile("raf.log");

=cut

# setLogFile : arguments : $_[0] : new name for log file
#              returns: nothing
sub setLogFile {
    my $self = shift;
    $self->{logFile} = $_[0] if defined $_[0];
}

=head2 setRAFFile(string $RAFFile)

 Usage   : $raf->setRAFFile($RAFFile)
 Returns : nothing
 Args    : RAF file name

 Sets the RAF map file name to $RAFFile. Note: This change is not reflected in
 the RAF object until after setIndexFile() is called. 

 Sample use:

  my $raf = RAF::new();
  $raf->setRAFFile("astral.new");

=cut

# setRAFFile : arguments : $_[0] : new name for RAF map file
#              returns: nothing, undefines %idx
sub setRAFFile {
    my $self = shift;
    if (!defined $_[0]) {
	die "argument to setRAFFile() not defined, $!\n";
    } elsif (!(-e $_[0])) {
	die "error in setRAFFile(): RAF file does not exist: $_[0],$!\n";
    }
    $self->{RAFmapFile} = $_[0] if defined $_[0];
    undef $self->{idx};
}

################################################################################
########                      ACCESSOR FUNCTIONS                      ##########
################################################################################

=head2 getIndexFile()

 Usage   : $raf->getIndexFile()
 Returns : nothing
 Args    : none

 Returns the file used to hold the index hash for the RAF object. 
 Sample use:

  my $raf = RAF::new();
  my $indexFileName = $raf->getIndexFile();

=cut

# getIndexFile : arguments : none
#                returns : index file for PDB chain obj
sub getIndexFile {
    my $self = shift;
    return $self->{indexFile};
}

=head2 getLogFile()

 Usage   : $raf->getLogFile()
 Returns : nothing
 Args    : none

 Returns the log file name, which contains various error messages from index
 hash creation.

 Sample use:

  my $raf = RAF::new();
  my $logname = $raf->getLogFile();

=cut

# getLogFile : arguments : none
#              returns : log file for PDB chain obj
sub getLogFile {
    my $self = shift;
    return $self->{logFile};
}

=head2 getRAFFile()

 Usage   : $raf->getRAFFile()
 Returns : nothing
 Args    : none

 Returns the RAF map file name.

 Sample use:

  my $raf = RAF::new();
  my $RAFFile = $raf->getRAFFile();

=cut

# getRAFFile : arguments : none
#              returns : RAF map file for PDB chain obj
sub getRAFFile {
    my $self = shift;
    return $self->{RAFmapFile};
}

# private method...
# getIndexHash : arguments : none
#                returns : index hash for PDB chain obj
sub getIndexHash {
    my $self = shift;
    return %{ $self->{idx} };
}

=head2 getIndexComment()

 Usage   : $raf->getIndexComment()
 Returns : nothing
 Args    : none

 Returns the character or sequence of characters that indicate a
 comment in the RAF file. 
 
 Sample use:

  my $raf = RAF::new();
  my $comment = $raf->getIndexComment();

=cut

# getIndexComment : arguments : none
#                   returns : index comment char for PDB chain obj
sub getIndexComment {
    my $self = shift;
    return $self->{indexSkip};
}

=head2 getIndexKeyStart()

 Usage   : $raf->getIndexKeyStart()
 Returns : nothing
 Args    : none

 Returns the number of characters to skip from the beginning of a RAF line
 before getting the key. 

 Sample use:

  my $raf = RAF::new();
  my $keyStart = $raf->getIndexKeyStart();

=cut

# getIndexKeyStart : arguments : none
#                    returns : index key start for PDB chain obj
sub getIndexKeyStart {
    my $self = shift;
    return $self->{indexKeyStart};
}

=head2 getIndexKeyLength()

 Usage   : $raf->getIndexKeyLength()
 Returns : nothing
 Args    : none

 Returns the key length used in creation of the index hash. 
 Sample use:

  my $raf = RAF::new();
  my $keyLength = $raf->getIndexKeyLength();

=cut

# getIndexKeyLength : arguments : none
#                     returns : index key length for PDB chain obj
sub getIndexKeyLength {
    my $self = shift;
    return $self->{indexKeyLength};
}

=head2 printme()

 Usage   : $raf->printme()
 Returns : nothing
 Args    : none

 Prints a brief description of the RAF object, with information including: 
  * RAF map file 
  * index file 
  * index creation variables (comment character, key start, key length)

 Sample use:

  my $raf = RAF::new();
  $raf->printme();

=cut

# printme : arguments : none
#           returns : nothing
# Description : prints a description of the RAF object
sub printme {
    my $self = shift;
    printf("indexFile = %s\n",$self->getIndexFile());
    printf("logfile = %s\n",$self->getLogFile());
    printf("raffile = %s\n",$self->getRAFFile());
    printf("indexcomment = %s\n",$self->getIndexComment());
    printf("indexkeystart = %s\n",$self->getIndexKeyStart());
    printf("indexkeylength = %s\n",$self->getIndexKeyLength());
}

################################################################################
########                     RAF MODULE FUNCTIONS                     ##########
################################################################################

=head2 getAllChains()

 Usage   : $raf->getAllChains()
 Returns : reference to array of strings
 Args    : none

 Returns a reference to an array of strings containing all PDB+chain IDs in the
 RAF object. Note: The strings are indexed in the array in no particular order. 

 Sample use:

  my $raf = RAF::new();

  # get list of all PDB+chainIDs in this RAF object
  my $chainNames_r = $raf->getAllChains();

=cut

# getAllChains : arguments : none
#                returns : REFERENCE TO array of strings containing all
#                          chain+PDB IDs
sub getAllChains {
    my $self = shift;
    my %idx = %{ $self->{idx} };
    my @returnVal = (keys %idx);
    return \@returnVal;
#    return \(keys %idx);
}

=head2 getChain(string $ID)

 Usage   : $raf->getChain($ID)
 Returns : PDBChain object
 Args    : PDB+chain ID in string form

 Returns the single PDBChain object given by the PDB+chain $ID, or undef if no
 such chain is found.

 Sample use:

  my $raf = RAF::new();

  # get chain corresponding to hemoglobin
  my $chain = $raf->getChain("101m_");

=cut

# getChain : arguments : $_[0] : a PDB+chain ID (ie "1abcA")
#            returns : PDBChain object representing $_[0], or null
#                      if $_[0] not found
sub getChain {
    my $self = shift;

    if (!defined $_[0]) {
	die "arguments not defined in raf::getChain(), $!\n";
    }

    my $val = ${ $self->{idx} }{$_[0]};
    if (defined $val) {
	open(MAP,"$self->{RAFmapFile}");
	seek(MAP,$val,0);
	my $map = <MAP>;
	return PDBChain::new($map);
	close(MAP);
    }

    return ();
}

=head2 getRawChain(string $ID)

 Usage   : $raf->getRawChain($ID)
 Returns : RAF line
 Args    : PDB+chain ID in string form

 Returns the RAF line given by the PDB+chain $ID, or undef if no such chain is
 found.

 Sample use:

  my $raf = RAF::new();

  # get chain corresponding to hemoglobin
  my $chain = $raf->getRawChain("101m_");

=cut

# getRawChain : arguments : $_[0] : a PDB+chain ID (ie "1abcA")
#               returns : RAF line containing chain, or null if not found.
#                       
sub getRawChain {
    my $self = shift;

    if (!defined $_[0]) {
	die "arguments not defined in raf::getRawChain(), $!\n";
    }

    my $val = ${ $self->{idx} }{$_[0]};
    if (defined $val) {
	open(MAP,"$self->{RAFmapFile}");
	seek(MAP,$val,0);
	my $map = <MAP>;
	close(MAP);
	return $map;
    }

    return ();
}

=head2 getChains(string $ID)

 Usage   : $raf->getChains($ID)
 Returns : reference array of PDBChain objects
 Args    : PDB+chain ID fragment

 Returns a reference to an array of PDBChain objects given by the PDB+chain $ID
 fragment, or undef if no such chains are found. Note: If any chains found, they
 are placed in the array in no particular order.

 Sample use:

  my $raf = RAF::new();

  # get all chains with PDB+chainID beginning with "101"
  my $chains_r = $raf->getChains("101");

=cut

# getChains : arguments : $_[0] : a PDB ID (ie "1abc" or "1ab")
#             returns : REFERENCE TO array of PDB chain objects representing
#                        $_[0], or null if none found
sub getChains {
    my $self = shift;
    my (%chainNames, @chainList) = ();
    my %idx = %{ $self->{idx} };

    $i = 0;
    foreach (keys %idx) {
        if ($_ =~ /^$_[0]/) {
            $chainNames{$_} = $idx{$_};
        }
    }

    open(MAP,"$self->{RAFmapFile}");

    foreach (keys %chainNames) {
	seek(MAP, $chainNames{$_},0);
        my $map = <MAP>;
	my $chain = PDBChain::new($map);
        $chainList[$i++] = $chain;
    }

    close(MAP);

    return \@chainList;
}

=head2 inRAF(string $ID)

 Usage   : $raf->inRAF($ID)
 Returns : 0 or 1
 Args    : PDB+chain ID

 Returns 1 if the PDB+chain ID is contained in the RAF object, 0 otherwise. 

 Sample use:

  my $raf = RAF::new();

  # is hemoglobin chain in the $raf?
  printf("is %sin RAF\n", ($raf->inRAF("101m_")) ? "" : "not ");

=cut

# inRAF : arguments : $_[0] : PDB+chain ID or PDB id
#         returns : 1 if ID is in RAF map file, 0 otherwise
sub inRAF {
    my $self = shift;
    (defined ${$self->{idx}}{$_[0]}) ? return 1 : return 0;
}


################################################################################
########                      INDEXING FUNCTIONS                      ##########
################################################################################

# makeIndex : arguments : none
#             returns: index made from RAF map file
sub makeIndex {
    my $self = shift;
    my %returnVal = ();
    my ($filepos, $key, $line);

    open (MAP,"$self->{RAFmapFile}");
    open (LOG,">$self->{logFile}");

    $filepos = 0;
    
    while ($line = <MAP>) {

	if ((defined $self->{indexSkip}) && ($line =~ /^$self->{indexSkip}/o)) {
	    &logmsg ("W: skipping $line");
	    $filepos = tell(MAP);
	    next;
	}

	if (($self->{indexKeyStart} ne "-1") && ($self->{indexKeyLength} ne "-1")) {
	    if (($self->{indexKeyLength} - $self->{indexKeyStart}) > length ($line)) {

		chomp $line;
		die "E: key length ($self->{indexKeyStart},$self->{indexKeyLength}) exceeds record length (",
		length($line),") |", substr ($line,0,10),"..|\n";
	    }
	    $key = substr($line,$self->{indexKeyStart},$self->{indexKeyLength});
	} else { ($key) = $line =~ /^(\S+)/; }

	$returnVal{$key} .= " " if defined $returnVal{$key};    # accept duplicate key
	$returnVal{$key} .= $filepos;
	$filepos = tell(MAP);
    }

    close(MAP); close(LOG);
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
#                modifies: index to hash ($_[0])
# Description: ties the index hash to the index file
sub tieIndexHash {
    my $self = shift;
    tie %{ $self->{idx} }, 'CDB_File', $_[0] or die "Could not tie $self->{indexFile}: $!\n";
}

1;
