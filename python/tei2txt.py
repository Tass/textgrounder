import sys
import os
import re
import gzip
import fnmatch

commaRE = re.compile(",")
nonAlpha = re.compile("[^A-Za-z]")


def cleanWord(word):
    word = word.lower()
    if len(word) < 2:
        word = ""
    return word


def strip_text (text):
    text = re.sub('&mdash+;', ' ', text)   # convert mdash to " "
    text = re.sub('&[A-Za-z]+;', '', text)   # convert ampersand stuff to ""
    text = re.sub('<[^>]*>', ' ', text)   # strip HTML markup
    #text = re.sub('[^A-Za-z]', ' ', text)   # convert non-letter chars to spaces
    text = re.sub('\s+', ' ', text)      # strip whitespace
    return text


directory_name = sys.argv[1]
output_raw_dir = sys.argv[2]

if not os.path.exists(output_raw_dir):
    os.makedirs(output_raw_dir)

files = os.listdir(directory_name)
for file in files:
    add_line = False
    write_text = False
    if fnmatch.fnmatch(file,"*.xml"):
        print "******",file
        newname = file[:-4]+".txt"
        raw_writer = open(output_raw_dir+"/"+newname,"w")
        file_reader = open(directory_name+"/"+file)
        text = ""
        for line in file_reader.readlines():
            text = text + " " + line.strip()

        raw_writer.write(strip_text(text))
        raw_writer.write("\n\n")
        raw_writer.close()
                