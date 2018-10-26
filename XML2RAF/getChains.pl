#!/lab/bin/perl
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

# get chains and chain type from XML file

use Astral::Xml2Raf;

$file = shift;
$output = shift;

die "Usage: $0 XML_FILE OUTPUT\n" unless $output;

my $pdb_xml_fh = new IO::File;
$pdb_xml_fh->open($file, '<:gzip')
 or die "Can't read $file\n";

open (OUT, ">$output") or die ("Cannot create file: $output\n");

my $pdbml = Astral::Xml2Raf::parse_pdbml($pdb_xml_fh);
delete $pdbml->{revdat};
for my $chain (sort keys %$pdbml) {
    # skip multi-letter chains
    next if (length($chain) > 1);

    my $rc   = $pdbml->{$chain};
    my $chainType = $rc->{pdbx_type};
    my $body = $rc->{body};
    my $bl = length($body)/7;
    my $nuc = $rc->{nucleotide_count};
    print OUT ("$chain\t$chainType\t$bl\t$nuc\n");
}

close OUT;
