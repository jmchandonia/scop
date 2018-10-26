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

package SCOPTree;

sub new {
    my $self;
    my %childrenhash = ();
    $self->{id} = (defined $_[0]) ? $_[0] : -1;    # -1 for root of tree
    $self->{childrenhash} = \%childrenhash;
    $self->{isLeaf} = 0;
    bless ($self);
    return $self;
}

# id looks like 234.534.65.234.345.23 , or any substring
sub getDomain {  # given id in $_[0], result in array reference $_[1];
    my $self = shift;
    my ($id, $result_r) = @_;
    my @id = split(/\./,$id);
    my %hash = %{$self->{childrenhash}};

    if ($self->{id} eq $id) {      # return all domains under current one
#	print("in 1\n");
	if ($self->{isLeaf}) {    # at leaf
	    foreach (values %hash) {
#		print("pushing $_\n");
		push(@{$result_r}, $_);
	    }
	} else {  # not at leaf
	    foreach (keys %hash) {
		$hash{$_}->getDomain( $hash{$_}->{id},$result_r);
	    }
	}

    } elsif($self->{id} eq $id[0]) {
#	print("in 2\n");
	foreach (keys %hash) {
	    if ($hash{$_}->{id} eq $id[1]) {
		$hash{$_}->getDomain( join(".", @id[1..$#id]),$result_r);
		return;
	    }
	}

    } elsif ($self->{id} eq -1) {
#	print("in 3\n");
        foreach (keys %hash) {
            if ($hash{$_}->{id} eq $id[0]) {
                $hash{$_}->getDomain($id,$result_r);
                return;
            }
        }

    } else {      # not found
#	print("in 4\n");
	return -1;
    }
}

sub addDomain {  # number id in $_[0], domain name in $_[1]
    my $self = shift;
    my ($id, $dom) = @_;
    my @id = split(/\./,$id);
    my $hash_r = $self->{childrenhash};

#    print("id = $id\n");

    if ($self->{isLeaf}) {    # at leaf
#	print("leaf id = $id\n");
	$$hash_r{$id} = $dom;

    } else {  # not at leaf
#	print("in else, id = $id\n");
	my $index = ($self->{id} == -1) ? 0 : 1;
#	print("index = $index\n");
	if (!defined $$hash_r{$id[$index]}) {  # make new node
#	    print("not defined\n");
	    my $newnode = SCOPTree::new($id[$index]);
#	    print("created new node $id[$index]\n");
	    $newnode->{isLeaf} = 1 if ((scalar @id == 2) && $index);
	    $$hash_r{$id[$index]} = $newnode;
	    $newnode->addDomain( join(".", @id[$index..$#id]), $dom);
	} else {
#	    print("adding to existing\n");
	    my $node = $$hash_r{$id[$index]};
	    $node->addDomain( join(".", @id[$index..$#id]), $dom);
	}
    }

#    $self->{childrenhash} = \%hash;
#    print("hash\n");
#    foreach (keys %hash) {
#	print("$_\n");
#    }
#    print("children\n");
#    foreach (keys %{$self->{childrenhash}}) {
#        print("$_\n");
#    }
}

1;
