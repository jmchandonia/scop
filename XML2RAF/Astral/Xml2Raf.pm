#!/usr/bin/perl
#
#    Copyright (c) 2008-2018, The Regents of the University of California
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

use strict;
use warnings;

package Astral::Xml2Raf;

=head1 NAME

Astral::Xml2Raf - Parse PDBML files into RAF format

=head1 SYNOPSIS

use Astral::Xml2Raf;

$raf_lines = Astral::Xml2Raf::create_RAF_lines($pdbid);

# or

$raf_lines = Astral::Xml2Raf::create_RAF_lines($pdbid, '080101');

=cut

##########################
# xml2raf.pl
##########################
#
# Parse PDBML files into RAF format
#
# Author: Degui Zhi, Dave Howorth, John-Marc Chandonia
#
# 2007-09-20 dz  Initial version loaded from xml2raf_with_one_letter_code.pl
# 2007-10-10 dz  Development to make a first working version
# 2007-10-11 dz  add translation table loading code
# 2007-10-15 dz  alpha version. tested over 1.69-raf-edit and produced
#                reasonable results
# 2007-10-24 jmc  added search for obsolete files, manual translations,
#                 checks for nucleic acids
# 2007-10-25 dz  added rule IVb to use <struct_ref_seq_difCategory>
# 2007-11-20 dz  added handling of insertion code
# 2008-01-07 djh v0.08	Local copy
# 2008-01-07 djh v0.09	Set shebang line to standard perl location
# 2008-01-07 djh v0.10	Added $C and set file locations to my paths
# 2008-01-08 djh v0.11	Added error checks to all calls to open() and
# 			use separate arguments for efficiency & security
# 2008-01-08 djh v0.12	use strict and use warnings and fix issues
#			including bug in caching %cyclized
# 2008-01-08 djh v0.13	Make source-code formatting more consistent
# 2008-01-08 djh v0.14	Use noatom PDB files to speed up processing
# 2008-01-08 djh v0.15	Read .gz files in-process for more speed
# 2008-01-08 djh v0.16	Make sure to read PDB file from correct release
# 2008-01-09 djh v0.17	Converted to a module
# 2008-01-10 djh v0.18	Ignore zero length chains
# 2008-01-10 djh v0.19	Add $nucleotide_translation{'du'} = 'x'
# 2008-01-10 djh v0.20	Only produce RAF lines for peptide chains
# 2008-01-11 djh v0.21	Fix modres code to handle insertion codes
# 2008-01-11 djh v0.22	Avoid caching cyclized hash elements for
# 			non-cyclized residues in chem_dic.txt
# 2008-01-16 djh v0.23	Add test of date format after error with 2J3S
# 2008-01-16 djh v0.24	All data in handle_char() must be appended
# 			because the parser may arbitrarily split text
# 			strings. Occurred in 2J3S PDBx:date field
# See http://search.cpan.org/~msergeant/XML-Parser-2.36/Parser.pm#Char_(Expat,_String)
# 2008-01-16 djh v0.25	Make Match() deal with nucleic acids (bug in 2OXM)
# 2008-01-16 djh v0.26	Added $nucleotide_count to catch errors in PDB
# 			chain classification (e.g. 2OXM)
# 2008-01-16 djh v0.27	Changed return value to be an array of lines
# 2008-08-19 jmc v0.3   Changed chem_dic.txt back to Degui's format
# 2008-08-29 jmc v0.31  Bug fixes re cyclized residues and numbering
# 2008-09-02 jmc v0.32  Fixed to handle blank chain ids (uses _)
# 2008-09-08 jmc v0.33  Fixed date handling to conform with RAF documentation
# 2008-09-09 jmc v0.34  Fixed bug in numbering of cyclized residue at
#                       beginning of chain (e.g., 2a52)
# 2008-09-12 jmc v0.4   General fix for numbering cyclized residues
# 2008-09-17 jmc v0.41  Fixed bug in translating modres/struct_ref_seq_dif
# 2008-09-24 jmc v0.42  Added check against microheterogeneity (but
#                       warning--creates bug with some pre-remediated xml files
#                       and obsolete entries, such as 4tsw, which re-use
#                       the same residue ids)
# 2008-09-26 jmc v0.43  Bug in struct_ref_seq_dif parsing
# 2008-09-29 jmc v0.44  Removed extra unused column in chem dict cache
#                       because of warnings on initial generation
# 2008-10-13 jmc v1.0   Initial public release
# 2008-10-15 djh v1.01  Fix to prevent wrong microheterogeneity calls
#                       if parser is called twice
# 2009-04-02 jmc v1.02  Try to make RAF lines for all chains, even if they
#                       are classified as non-protein (to avoid PDB bug)
# 2009-06-23 jmc v1.03  Fix warning about nucleotides for chains that
#                       are correctly described as nucleotides
# 2015-01-14 jmc v1.04  Ignore two-letter chains in XML

our $VERSION = '1.04';

# TODO	Remove global variables
#
# TODO	Use XML::LibXML instead of XML::Parser
# 	(see http://perl-xml.sourceforge.net/faq/#cpan_modules)
#
# TODO	ParseChemDictionary should use an XML parser, instead of regexen

our $debug = 0;

#=======================================================================

use XML::Parser;
use IO::File;

##########################
# Parameters
##########################

our $C;

$C->{WWPDB_BASE}  = '/lab/db/pdb';
$C->{ASTRAL_BASE} = '/lab/proj/astral/';

# directories
$C->{XML_dir}     = 'xml-hash';
$C->{XML_obs_dir} = 'xml-obs-hash';

# 2008-01-07 djh
# Following file is downloaded from the 'Download chemical component
# dictionary' XML link on <http://remediation.wwpdb.org/downloads.html>
# The file itself is
#   <http://remediation.wwpdb.org/downloads/Components-rel-alt.xml.gz>
#
$C->{chem_dic_file}  = "$C->{WWPDB_BASE}/data/Components-rel-alt.xml.gz";
$C->{chem_dic_cache} = "$C->{ASTRAL_BASE}/bin/chem_dic.txt";

# translation table
my %standard_translation = qw ( ala a val v phe f pro p met m ile i leu l
  asp d glu e lys k arg r ser s thr t tyr y his h
  cys c asn n gln q trp w gly g glx z asx b unk x
  n/a . ace . ch3 . nh2 . for . fmt .);

my %nucleotide_translation = qw ( a x  t x  g x  c x  u x  n x
  da x dt x dg x dc x du x dn x);

# Load the translation table by parsing the chem-comp dictionary dynamically
#
my ($chem_translation) =
  ParseChemDictionary( $C->{chem_dic_file}, $C->{chem_dic_cache} );

#=======================================================================

# TODO GET RID OF THESE GLOBALS!

my %in;
my %pdbml;

my $chain;        # Chain letter
my $entity_id;    # number associated with each chain

my $pdbx_type;    # Chain type '...peptide...' etc
my %entity_type;  # map from entity_id to pdbx_type

my $PDB_rev_num;  # the revision number (i.e. modNum)

my $pdbx_one_letter_code;        # Sequence 1
my $pdbx_one_letter_code_can;    # Sequence 2

my $one_letter_code_translation; # Hashmap from  seq 1 => seq 2

my $conn_type;
my $conn_id;

my $residue;                     # Current residue
my $previous_resID;

# stacks of cyclized residues, to be handled when we encounter the
# next non-cyclized residue or the end of the chain.  Each is a
# hash of stacks, with the chain id being the key.
my %cyc_sr;
my %cyc_ar;
my %cyc_resID;

# residue ids seen in the each chain.  This is a hash of hashes, with
# the chain id being the first key.  Does not apply to cyclized residues.
my %seen_resID;

my %modres;

my $modres_chain;
my $modres_details;
my $modres_code;
my $modres_resID;
my $modres_aa;

my %struct_ref_seq_dif;

my $struct_ref_seq_dif_chain;
my $struct_ref_seq_dif_resID;
my $struct_ref_seq_dif_code;
my $struct_ref_seq_dif_aa;

#=======================================================================
#=======================================================================

=head2 create_RAF_lines

Returns the RAF lines corresponding to the chains of a particular PDB
entry. It takes one or two arguments:

=over

=item pdbid

The four-letter identifier of a PDB entry, in upper- or lower-case

=item release_date

The date of a PDB archive release in YYMMDD format, (as obtained by PAST)
Note that this is B<not> the release date of the individual entry
(a.k.a. I<revdat>) but of a weekly PDB release archive.
If this argument is not supplied, it defaults to the latest release.

=back

=cut

# The implementation is split into two functions.
#
# The first (create_RAF_lines) locates the XML file and opens a
# filehandle, which it passes to the second function ...
#
# The second (_create_RAF_lines) takes a filehandle and parses the
# contents.
#
# This implementation allows testing using XML files in non-standard
# places.
#
sub create_RAF_lines {
    my ( $pdbid, $release_date ) = @_;

    #$DB::single = 1;

    die "Don't recognize PDB entry id '$pdbid'\n"
      unless $pdbid =~ /^\d\w\w\w$/;

    $pdbid = lc $pdbid;

    if ($release_date) {
        die "Don't recognize date format of '$release_date'\n"
          unless $release_date =~ /^\d\d\d\d\d\d$/;
    }
    else {
        $release_date = 'latest';
    }

    warn "Doing $pdbid\n" if $debug;

    # Find the XML file for the PDB entry
    #
    my $release_dir = "$C->{WWPDB_BASE}/data/$release_date/snapshot/";

    #    my $noatom = '-noatom';	# Use noatom PDB files
    my $noatom = '';                   # Don't use noatom PDB files
    my ($hash) = $pdbid =~ /.(..)./;
    my $gzfile = "$release_dir/$C->{XML_dir}$noatom/$hash/$pdbid$noatom.xml.gz";

    # Look for obsolete entry if we didn't find normal entry
    $gzfile = "$release_dir/$C->{XML_obs_dir}/$hash/$pdbid.xml.gz"
      if not -e $gzfile or -z $gzfile;

    die "'$gzfile' not found or empty\n"
      if not -e $gzfile or -z $gzfile;

    # Open the compressed PDB entry with an IO layer to decompress it
    #
    my $pdb_xml_fh = new IO::File;
    $pdb_xml_fh->open( $gzfile, '<:gzip' )
      or die "Can't read $gzfile\n";

    return _create_RAF_lines( $pdbid, $pdb_xml_fh );
}

sub parse_pdbml {
    my ($pdb_xml_fh) = @_;

    #    my $xml_parser = new XML::Parser(Style => 'Debug');
    my $xml_parser = new XML::Parser(
        Handlers => {
            Start => \&handle_start,
            End   => \&handle_end,
            Char  => \&handle_char,
            Final => \&handle_final,
        }
    );
    my $pdbml = eval { $xml_parser->parse($pdb_xml_fh) };
    die 'error: ' . $@ if $@;

    return $pdbml;
}

sub _create_RAF_lines {
    my ( $pdbid, $pdb_xml_fh ) = @_;

    $pdbid = lc $pdbid;

    my $pdbml = parse_pdbml($pdb_xml_fh);

    # JMC: RAF date is documented as timestamp on file:
    my $filedate = ( stat $pdb_xml_fh )[9];
    my @arr      = localtime($filedate);
    my $date = sprintf( "%02d%02d%02d", $arr[5] % 100, $arr[4] + 1, $arr[3] );

    # JMC:  this is not the correct way of getting the date for RAF:
    # Get latest revdat and reformat for RAF
    # my $date = $pdbml->{revdat}->[-1];
    # die "Bad date '$date'" unless $date =~ /^\d\d\d\d-\d\d-\d\d$/;
    # $date =~ s/^..//;	# remove century
    # $date =~ s/-//g;	# remove field separators

    # delete this, or it shows up in list of chains:
    delete $pdbml->{revdat};

    # Format the results into a RAF line for each chain
    #
    my @raf_lines;

    for $chain ( sort keys %$pdbml ) {

        # skip multi-letter chains
        next if ( length($chain) > 1 );

        my $rc = $pdbml->{$chain};

        # next unless $rc->{pdbx_type} =~ /peptide/;

        # add cyclized residues from end of chain
        add_cyclized( $rc->{lastRes}, undef );

        my $body = $rc->{body};
        next unless defined $body;         # ignore zero length chains
        next unless length($body) > 14;    # ignore very short chains
             #warn "body(".length($body).")='$body'\n"
             #if $pdbid eq '1abw' and $chain eq 'L';

        if (   ( $rc->{nucleotide_count} == length($body) / 7 )
            && ( $rc->{pdbx_type} !~ /nucleotide/ ) )
        {
            warn "$pdbid$chain claims to be '$rc->{pdbx_type}' "
              . "but seems to be all nucleotides\n";
        }

        #warn "$chain firstRes not defined\n" unless defined $rc->{firstRes};
        #warn "$chain lastRes not defined\n" unless defined $rc->{lastRes};

        # need to change all 'M' to 'E' at end of chain.
        my $nr = length($body) / 7;
        for ( my $i = $nr - 1 ; $i >= 0 ; $i-- ) {
            if ( substr( $body, $i * 7 + 3, 1 ) eq "M" ) {
                substr( $body, $i * 7 + 3, 1, "E" );
            }
            else {
                $i = 0;
            }
        }

        # real output
        my $raf_line =
            "$pdbid$chain 0.02 38 $date 11101"
          . ( $rc->{notOneToOne} ? '0' : '1' )
          . sprintf( " %5s", $rc->{firstRes} )
          . sprintf( "%5s",  $rc->{lastRes} )
          . sprintf( "%s\n", $body );

        warn $raf_line if $debug;

        push @raf_lines, $raf_line;
    }

    return @raf_lines;
}

sub handle_char {
    my ( $p, $data ) = @_;

    if ( $in{"PDBx:date"} and $in{"PDBx:database_PDB_rev"} ) {
        $pdbml{revdat}->[$PDB_rev_num] .= $data;
    }

    if ( $in{"PDBx:entity_poly"} ) {
        if ( $in{"PDBx:type"} ) {
            $pdbx_type .= $data;
            $entity_type{$entity_id} = $pdbx_type;
        }

        if (    $in{"PDBx:pdbx_seq_one_letter_code"}
            and $pdbx_type =~ /peptide/ )
        {
            chomp $data;
            $pdbx_one_letter_code .= $data;
        }

        if (    $in{"PDBx:pdbx_seq_one_letter_code_can"}
            and $pdbx_type =~ /peptide/ )
        {
            chomp $data;
            $pdbx_one_letter_code_can .= $data;
        }
    }

    if (    $in{"PDBx:pdbx_poly_seq_scheme"}
        and $in{"PDBx:pdbx_poly_seq_schemeCategory"} )
    {
        $residue->{resID}    .= $data if $in{"PDBx:pdb_seq_num"};
        $residue->{ins_code} .= $data if $in{"PDBx:pdb_ins_code"};

        # $residue->{atomRes} = lc $data if $in{"PDBx:auth_mon_id"};
        # the above has problem since expat can break a single data field
        #	$residue->{atomRes} .= lc $data if $in{"PDBx:auth_mon_id"};
        $residue->{atomRes} .= lc $data if $in{"PDBx:pdb_mon_id"};

        #	if (length $residue->{atomRes} !=3)
        #	{ print STDERR "data=$data\n"; }
        $residue->{foundChain} .= $data if $in{"PDBx:pdb_strand_id"};
    }

    # parse out modres
    if ( $in{"PDBx:struct_conn"} ) {
        $conn_type .= $data if $in{"PDBx:conn_type_id"};
    }

    if ( $in{"PDBx:struct_conn"} and $conn_type =~ /modres/ ) {
        $modres_chain   .= $data if $in{"PDBx:ptnr1_auth_asym_id"};
        $modres_details .= $data if $in{"PDBx:details"};
        $modres_resID   .= $data if $in{"PDBx:ptnr1_auth_seq_id"};

        $data = lc $data;
        $modres_code .= $data if $in{"PDBx:ptnr1_auth_comp_id"};
        $modres_aa   .= $data if $in{"PDBx:pdbx_ptnr1_standard_comp_id"};
    }

    if ( $in{"PDBx:struct_ref_seq_dif"} ) {
        $struct_ref_seq_dif_chain .= $data if $in{"PDBx:pdbx_pdb_strand_id"};
        $struct_ref_seq_dif_resID .= $data if $in{"PDBx:pdbx_auth_seq_num"};
        $struct_ref_seq_dif_code  .= $data if $in{"PDBx:mon_id"};
        $struct_ref_seq_dif_aa    .= $data if $in{"PDBx:db_mon_id"};
    }

}    # end sub handle_char

# fill in cyclized residues between the previous and next uncyclized
# residue ids.  If either is undefined, we're at the end of the chain.
sub add_cyclized {
    my ( $resID1, $resID2 ) = @_;
    my ( $i, $j );

    return if ( !defined $cyc_sr{$chain} );
    return if ( $#{ $cyc_sr{$chain} } == -1 );

    # print ("$resID1, $resID2\n");

    # count total number of residues we need to number
    my $total_number = 0;
    for ( $j = 0 ; $j <= $#{ $cyc_sr{$chain} } ; $j++ ) {
        $total_number += length( $cyc_sr{$chain}[$j] );
    }

    my $starting_residue;    # if pseudonumbering

    # get numeric equivalents of resids at ends of gap
    my $resID1_num;
    my $resID2_num;
    if (   ( defined $resID1 )
        && ( $resID1 =~ /(-?\d+)/ ) )
    {
        $resID1_num = $1;
    }
    if (   ( defined $resID2 )
        && ( $resID2 =~ /(-?\d+)/ ) )
    {
        $resID2_num = $1;
    }

    # figure out what numbering scheme to use
    if (   ( defined $resID1 )
        && ( defined $resID2 ) )
    {
        if (   ( defined $resID1_num )
            && ( defined $resID2_num ) )
        {
            my $total_gap_length = $resID2_num - $resID1_num - 1;
            if ( $total_gap_length == $total_number ) {
                $starting_residue = $resID1_num + 1;
            }

            # otherwise, use original residue ids
        }
    }
    elsif ( defined $resID1 ) {
        if ( defined $resID1_num ) {
            $starting_residue = $resID1_num + 1;
        }
    }
    elsif ( defined $resID2 ) {
        if ( defined $resID2_num ) {
            $starting_residue = $resID2_num - $total_number;
        }
    }

    # add original residues to raf line
    for ( $j = 0 ; $j <= $#{ $cyc_sr{$chain} } ; $j++ ) {
        my $ar    = $cyc_ar{$chain}[$j];
        my $sr    = $cyc_sr{$chain}[$j];
        my $resID = $cyc_resID{$chain}[$j];

        # verify $sr and $ar are equal
        if ( ( $ar ne $sr ) and ( $ar ne "." ) ) {
            warn
"Mismatched cyclized residue at %d: seqRes=#$sr#; atomRes=#$ar#\n",
              $residue->{resID}
              if $ar ne $sr;
        }

        my $res_i;
        for $i ( 1 .. length($sr) ) {
            my $sr_i = substr( $sr, $i - 1, 1 );
            if ( $ar eq "." ) {
                if ( !defined $pdbml{$chain}->{firstRes} ) {
                    $res_i = "B";
                }
                else {
                    $res_i = "M";
                }
                $pdbml{$chain}->{body} .= sprintf( "   %s .%s", $res_i, $sr_i );
            }
            else {
                my $ar_i = substr( $ar, $i - 1, 1 );

                if ( defined $starting_residue ) {

                    # pseudonumbering:
                    $res_i = $starting_residue . " ";
                    $starting_residue++;
                }
                else {
                    # use original numbering for all:
                    $res_i = $resID;
                }
                $pdbml{$chain}->{body} .=
                  sprintf( "%5s%s%s", $res_i, $ar_i, $sr_i );

                $pdbml{$chain}->{firstRes} = $res_i
                  unless $pdbml{$chain}->{firstRes};

                $pdbml{$chain}->{lastRes} = $res_i;
            }
        }
        if ( $ar ne "." ) {
            $previous_resID = $res_i;
        }
    }
    @{ $cyc_sr{$chain} }    = ();
    @{ $cyc_ar{$chain} }    = ();
    @{ $cyc_resID{$chain} } = ();
}

sub handle_end {
    my ( $p, $el ) = @_;

    #    $in{$el} = 0;
    delete $in{$el};

    # handle modres

    if ( ( $el eq "PDBx:struct_conn" ) and ( $conn_type =~ /modres/ ) )
    {    # dont forget to reset conn_id;
            # print "modres '$modres_chain' '$modres_resID' is $modres_aa\n";
            # return if uc($modres_chain) ne $xmlChain;
        $modres{$modres_chain}{$modres_resID} = lc $modres_aa;
        $conn_type = "";
    }

    if ( $el eq "PDBx:struct_ref_seq_dif" ) {

        # return if uc($modres_chain) ne $xmlChain;
        $struct_ref_seq_dif{$struct_ref_seq_dif_chain}
          {$struct_ref_seq_dif_resID} = lc $struct_ref_seq_dif_aa;
        return;
    }

    if ( $el eq "PDBx:pdbx_seq_one_letter_code_can" ) {

        # match $pdbx_one_letter_code and $pdbx_one_letter_code_can
        # to derive a local translation table
        #
        $one_letter_code_translation =
          Match( $pdbx_one_letter_code, $pdbx_one_letter_code_can );
        return;
    }

    if ( $el eq "PDBx:pdbx_poly_seq_scheme" ) {
        $chain = $residue->{foundChain};
        if ( !defined $chain ) {
            $chain = "_";
        }

        # printf "n=%s sr=%s ar=%s\n", $n, $seqRes, $atomRes;
        if (
            ( defined $residue->{resID} )
            && (   ( $residue->{resID} =~ /\A\d+\Z/ )
                || ( $residue->{resID} =~ /\A-\d+\Z/ ) )
          )
        {
            # append ins code; assume space if numeric
            $residue->{resID} .=
              $residue->{ins_code} ? $residue->{ins_code} : " ";
        }

        # Retrieve the type of chain (peptide, nucleotide etc)
        $pdbml{$chain}->{pdbx_type} = $entity_type{$entity_id};

        # now the translation module

        my $nucleotide_count = $pdbml{$chain}->{nucleotide_count} || 0;
        my $sr = TranslateByRules(
            $residue->{seqRes}, $residue->{resID},
            'seqRes ',          \$nucleotide_count
        );
        $pdbml{$chain}->{nucleotide_count} = $nucleotide_count;

        my $ar =
          TranslateByRules( $residue->{atomRes}, $residue->{resID}, 'atomRes' );

        undef $previous_resID unless $pdbml{$chain}->{firstRes};

        # special handling of cyclized; need to put them on hold
        # until we know the next residue number
        if ( length($sr) > 1 ) {
            if ( $ar eq "." ) {
                $pdbml{$chain}->{notOneToOne} = 1;
            }

            push @{ $cyc_sr{$chain} },    $sr;
            push @{ $cyc_ar{$chain} },    $ar;
            push @{ $cyc_resID{$chain} }, $residue->{resID};
        }
        else {
            add_cyclized( $previous_resID, $residue->{resID} );

            if ( $ar eq "." ) {
                if ( !defined $pdbml{$chain}->{firstRes} ) {
                    $residue->{resID} = "B ";
                }
                else {
                    $residue->{resID} = "M ";
                }
            }
            else {
                $pdbml{$chain}->{firstRes} = $residue->{resID}
                  unless $pdbml{$chain}->{firstRes};

                $pdbml{$chain}->{lastRes} = $residue->{resID};
            }

            if ( $ar ne "." or $sr ne "." ) {
                if ( $sr eq "." or $ar eq "." ) {
                    $pdbml{$chain}->{notOneToOne} = 1;
                }

                if (   ( !$seen_resID{$chain}{ $residue->{resID} } )
                    || ( $ar eq "." ) )
                {

                    $seen_resID{$chain}{ $residue->{resID} } = 1;

                    $pdbml{$chain}->{body} .=
                      sprintf( "%5s%s%s", $residue->{resID}, $ar, $sr );

                }
                else {
                    warn
                      "Possible microheterogeneity; skipping $chain, residue "
                      . $residue->{resID} . "\n";
                }
            }

            if ( $ar ne "." ) {
                $previous_resID = $residue->{resID};
            }
        }

        return;

    }    # end if ($el eq "PDBx:pdbx_poly_seq_scheme")
}

sub handle_start {
    my $p  = shift;
    my $el = shift;

    $in{$el} = 1;

    # Start of entry initialization - djh
    #
    if ( $el eq 'PDBx:datablock' ) {
        %in                 = ();
        %modres             = ();
        %struct_ref_seq_dif = ();
        %entity_type        = ();
        %pdbml              = ();
        %seen_resID         = ();
    }

    if ( $el eq 'PDBx:database_PDB_rev' ) {
        while (@_) {
            my $att = shift;
            my $val = shift;
            $PDB_rev_num = $val if $att eq 'num';
        }
    }

    # Start of chain
    #
    if ( $el eq "PDBx:entity_poly" ) {
        $pdbx_type                = '';
        $pdbx_one_letter_code     = "";
        $pdbx_one_letter_code_can = "";

        while (@_) {
            my $att = shift;
            my $val = shift;
            $entity_id = $val if $att eq 'entity_id';
        }
    }

    if (    $el eq "PDBx:pdbx_poly_seq_scheme"
        and $p->current_element eq "PDBx:pdbx_poly_seq_schemeCategory" )
    {
        $residue = ();

        # $residue->{atomRes} = '';
        while (@_) {
            my $att = shift;
            my $val = shift;
            $residue->{seqRes} = lc $val if $att eq "mon_id";
            $entity_id = $val if $att eq 'entity_id';
        }

        return;
    }

    # deal with attributes
    if ( $el eq "PDBx:struct_conn" ) {
        $modres_chain   = "";
        $modres_details = "";
        $modres_code    = "";
        $modres_resID   = "";
        $modres_aa      = "";
        $conn_type      = "";

        while (@_) {
            my $att = shift;
            my $val = shift;
            $conn_id = $val if $att eq "id";
        }
    }

    # deal with <PDBx:struct_ref_seq_difCategory>
    if ( $el eq "PDBx:struct_ref_seq_dif" ) {
        $struct_ref_seq_dif_chain = '';
        $struct_ref_seq_dif_resID = '';
        $struct_ref_seq_dif_code  = '';
        $struct_ref_seq_dif_aa    = '';
    }

    # deal with attributes
}

# Return the data structure resulting from the parse
#
sub handle_final {
    return \%pdbml;
}

# Derive a local translation table
#
sub Match {
    my ( $one, $translated ) = @_;

    #    warn "Match($one, $translated)\n" if $debug;

    my $table;    # result to be

    my @one  = split //, lc($one);
    my @tran = split //, lc($translated);
    my $len  = scalar @tran;
    my $j    = 0;
    for my $i ( 0 .. $len - 1 ) {
        my $t = $tran[$i];
        my $o = $one[$j];

        #warn "i=$i o=$o t=$t\n" if $debug;
        if ( $o eq '(' ) {

            #	    $o = join "", @one[$j+1..$j+3];
            #	    $j += 5;

            $o = $one[ ++$j ];
            $o .= $one[$j] while $one[ ++$j ] ne ')';
            $j++;

            #warn "o=$o t=$t table-o=$$table{$o}\n";

            next if ( defined $$table{$o} and $$table{$o} eq $t );
            next if $o eq 'n/a';
            next if $o eq 'ace';
            next if $o eq 'nh2';
            if ( $$table{$o} ) {
                die "$chain: different translation table $o->$t\n";
            }
            $$table{$o} = $t;
        }
        elsif ( $t ne $o ) {
            warn "Match($one, $translated)  i=$i j=$j o=$o t=$t\n";
            die "$chain: Wrong matching $o-$t\n";
            $j++;
        }
        else { $j++ }
    }

    return $table;
}

sub ParseChemDictionary {
    my ( $chem_dic_file, $chem_dic_cache ) = @_;

    warn "loading chem dictionary ...\n" if $debug;

    my %translation;

    # first time parsing the dictionary will write the result into chem_dic.txt
    # so that future runs will not need to reparse the original big xml file
    if ( -e $chem_dic_cache ) {
        open( CD, '<', $chem_dic_cache )
          or die "Can't read '$chem_dic_cache'\n";
        warn "loading chem_dic_cache\n" if $debug;

        while (<CD>) {
            my ( $a, $t, $c ) = split /\s+/;
            $translation{$a} = $t;
        }
        close CD;

        return \%translation;
    }

    # Read the dictionary file and build the hash tables
    #
    open CD, '<:gzip', $chem_dic_file
      or die "Can't read '$chem_dic_file'\n";

    my ( $id, $initial_date, $modified_date, $parent_comp_id );

    while (<CD>) {
        if (/<PDBx:chem_comp id=\"(\w\w\w)\">/) {
            $id = lc $1;
            $parent_comp_id = $initial_date = $modified_date = '';
        }
        elsif (/<PDBx:pdbx_initial_date>(.+)<\/PDBx:pdbx_initial_date>/) {
            $initial_date = $1;
        }
        elsif (
            /<PDBx:mon_nstd_parent_comp_id>(.+)<\/PDBx:mon_nstd_parent_comp_id>/
          )
        {
            $parent_comp_id = lc $1;
        }
        elsif (/<PDBx:pdbx_modified_date>(.+)<\/PDBx:pdbx_modified_date>/) {
            $modified_date = $1;
        }
        elsif ( /<\/PDBx:chem_comp>/ and not $translation{$id} ) {
            if ( $standard_translation{$parent_comp_id} ) {

                # single aa parent id case
                $translation{$id} = $standard_translation{$parent_comp_id};
            }
            else {
                my @aa_items = split ",", $parent_comp_id;
                next unless @aa_items;
                foreach (@aa_items) {
                    if (/(\w\w\w)/) {
                        $translation{$id} .= $standard_translation{$1};
                    }
                }
            }
        }

    }    # end while(<CD>)

    close CD;

    warn sprintf( "Done. %d entries loaded.\n", scalar keys %translation )
      if $debug;

    # Save the translation for reference
    #
    open( CD, '>', $chem_dic_cache ) or die "Can't create '$chem_dic_cache'\n";

    for my $k ( sort keys %translation ) {
        printf CD "$k\t%s\n", $translation{$k};
    }

    close CD;

    return \%translation;
}

sub TranslateByRules {

    # Rules:

    # I.   Accept standard translation
    # Ib.  Warn about nucleotide
    # II.  if cyclized, trust the chem dic
    # III. if non-cyclized, X in one_letter_code, use modres translation.
    # IV.  if modres translation is non-standard, trust the one_letter_code
    #      translation
    # IVb. if there is no modres translation for a residue, but it's
    #      still translating to X, then try to use the
    #      struct_ref_seq_difCategory record.
    # V.   Multiple modres for a single residue: show disagreement in modres
    #      and one_letter_code, but correctable by later modres.
    #      e.g., 1mqxA 2, ABA

    my ( $res, $resID, $s_or_a, $nucleotide_count ) = @_;

    $res = '' unless defined $res;

    my $resID_nospace = $resID;
    $resID_nospace =~ s/ $//;

    #$debug = 2 unless $resID =~ /^\d+ $/;
    #$debug = $chain eq 'B' ? 2 : 1;
    warn "TranslateByRules[$chain]($res, $resID, $s_or_a)\n" if $debug > 1;

    my $sr;
    my $rule;

    if ( exists $standard_translation{$res} ) {
        $sr   = $standard_translation{$res};
        $rule = "";
    }
    elsif ( $res eq '' ) {
        $sr   = '.';
        $rule = "empty res";
    }
    elsif ( exists $nucleotide_translation{$res} ) {
        $sr   = $nucleotide_translation{$res};
        $rule = "nucleotide";
        ++$$nucleotide_count if defined $nucleotide_count;
    }
    elsif ( $chem_translation->{$res} ) {
        $sr   = $chem_translation->{$res};
        $rule = "chemical component dictionary";
    }
    elsif ( my $modres = $modres{$chain}{$resID_nospace} ) {
        if ( my $trans = $standard_translation{$modres} ) {
            $sr   = $trans;
            $rule = "modres";
        }
    }
    elsif ( my $ref_seq_dif = $struct_ref_seq_dif{$chain}{$resID_nospace} ) {
        if ( my $trans = $standard_translation{$ref_seq_dif} ) {
            $sr   = $trans;
            $rule = "ref_seq_dif";
        }
    }
    elsif ( $one_letter_code_translation->{$res} ) {
        $sr   = lc( $one_letter_code_translation->{$res} );
        $rule = "one letter code";
    }
    else {
        $sr   = "x";
        $rule = "all failed";
    }

    $sr = "." unless $sr;

    warn sprintf "$chain '%4s'  '%3s' $sr # $s_or_a $rule\n", $resID, $res

      #      if $debug > 1 and $rule;
      if $debug > 1;

    return $sr;
}

1;
