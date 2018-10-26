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

# wrapper to use Xml2Raf module, one file at a time

use Astral::Xml2Raf;

$file = shift;
$raf_out = shift;

die "Usage: $0 XML_FILE OUTPUT_IN_RAF_FORMAT\n" unless $raf_out;

my $pdb_code;
if ($file =~ /(\d\w\w\w)\.xml/) {
    $pdb_code = $1;
}
else {
    die ("couldn't find pdb code");
}

my $pdb_xml_fh = new IO::File;
$pdb_xml_fh->open($file, '<:gzip')
 or die "Can't read $file\n";

open (RAF, ">$raf_out") or die ("Cannot create file: $raf_out\n");

my @raf = Astral::Xml2Raf::_create_RAF_lines($pdb_code,$pdb_xml_fh);

foreach $line (@raf) {
    print RAF $line;
}
close RAF;
