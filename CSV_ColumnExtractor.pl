#!/usr/bin/perl -w

########################################################
# This script extracts data columns identified by      #
# their title from a tab delimetered list.             #
# It takes three arguments: The input file that        #
# contains the data to be extracted, the output file   #
# that receives the data, and a key to access the data #
# column to be extracted.                              #
########################################################

use strict;
use warnings;

use Text::CSV;
use Carp;
use File::Basename;
use File::Spec;

########################################################
# Min and Max functions                                #
########################################################
sub max ($$) { $_[$_[0] < $_[1]] }
sub min ($$) { $_[$_[0] > $_[1]] }

#set command line arguments
my ($infi, $outfile, $idcol) = @ARGV;

# Get date, larval age, and genotype from input file
$infi =~ m/([0-9]{4}-[0-9]{2}-[0-9]{2}).*((\d+(\.{1}\d+){1})[A-Za-z]+_[A-Za-z0-9]+_[0-9]+)/;
my $title = "";

if(defined $1)
{
	$title = $1 . "_" . $2;
}
else
{
	# For some reason I cannot make it match a float and an integer at the same time.
	$infi =~ m/([0-9]{4}-[0-9]{2}-[0-9]{2}).*([0-9]+[A-Za-z]+_[A-Za-z0-9]+_[0-9]+)/;
	$title = $1 . "_" . $2;
}

print "Add ", $idcol, " of ", $title, " to ", $outfile, "\n";

# Read in data from whatever seperated values format, use tab as seperator
my $csv = Text::CSV->new({
  sep_char => "\t"
});

open(my $fh, "<:encoding(UTF-8)", $infi) or die "Can't open $infi: $!";

# Get column names
$csv->column_names($csv->getline($fh));

my @column = ();

while(my $hr = $csv->getline_hr($fh))
{
	# Read data by certain column ID
	push(@column, $hr->{$idcol})
}

close $fh;

if(-e $outfile)
{
	my @outtable = ();

	open($fh, "<:encoding(UTF-8)", $outfile) or die "Can't open $outfile: $!";
	while (<$fh>)
	{
		$_ =~ s/[\r\n]+//g; # Clean line endings
		push(@outtable, $_);
	}
	close $fh;

	open($fh, ">:encoding(UTF-8)", $outfile) or die "Can't open $outfile: $!";
	print $fh $outtable[0], "\t", $title, "\n";
	for(my $i = 0; $i < max(scalar(@column), scalar(@outtable)-1); $i++)
	{
		if(defined($column[$i]))
		{
			if(defined($outtable[$i+1]))
			{
				print $fh $outtable[$i+1], "\t", $column[$i], "\n";
			}
			else
			{
				my $countTabs = ($outtable[0] =~ tr/\t//);
				print $fh "\t" x $countTabs, "\t", $column[$i], "\n";
			}
		}
		elsif(defined($outtable[$i+1]))
		{
			print $fh $outtable[$i+1], "\t\n";
		}
	}
	close $fh;
}
else
{
	open($fh, ">:encoding(UTF-8)", $outfile) or die "Can't open $outfile: $!";
	print $fh $title, "\n";
	for(my $i = 0; $i < scalar(@column); $i++)
	{
		if(defined($column[$i]))
		{
			print $fh $column[$i], "\n";
		}
		else
		{
			print $fh "\n";
		}
	}
	close $fh;
}

