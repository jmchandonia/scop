#!/lab/bin/perl
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

# by JMC
# finds raf errors of 3 types
#  1) residue in seqres, not in atom
#  2) residue in atom, not in seqres
#  3) residue different in atom, seqres
#  4) possible malformed RAF entry

use strict;

use RAF;
use PDBChain;

MAIN: {
    my $raf = RAF::new(shift, shift);

    my $output_file = shift or die ("no output file given");
    open (OUTPUT,">$output_file") or die ("couldn't open output $output_file");

    my @chains = @{$raf->getAllChains()};
    foreach my $chainName (@chains) {
	my $chain = $raf->getChain($chainName);
	my $errorString = $chain->checkSyntax();
	print OUTPUT $errorString if defined $errorString;
    }
    close (OUTPUT);
}
