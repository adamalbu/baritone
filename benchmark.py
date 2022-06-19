import os
from math import sqrt

commits = [
    "1915d542d4f9cd1e1e22aacbd0a2c2303cab68b8",
    "7ee1ac771c46f51cb2811d1f19934e5aabe357e2",
    "5be1a3b45798c3c70ea9df2da4d40af189bfe5be",
]

labels = [
    "ArrayList",
    "ImmutableSet",
    "ImmutableList",
]

title = "Nodes per Second"

repetitions = 10

command = "export BARITONE_AUTO_TEST=true; bash ./gradlew runClient --offline"

inpath = "run/baritone/autotest.txt"

outpath = "results.csv"



statistics = []
with open(outpath, "w") as out:
    for i in range(repetitions):
        for commit, label in zip(commits, labels):
            print("Running %s / Repetition %s" % (label, i));
            os.system("git checkout " + commit)
            os.system(command)
            with open(inpath, "r") as in_:
                lines = list(map(str.strip, in_.readlines()))
            out.write("%s%s, " % (label, i))
            out.write(", ".join(lines))
            out.write("\n")
            data = [int(line) for line in lines if line.isdigit()]
            average = sum(data) / len(data)
            sigma = sqrt(sum((x - average)**2 for x in data) / len(data))
            statistics.append((label + str(i), average - sigma, average, average + sigma))
    out.write("\n")
    out.write(title + ", average - sigma, average, average + sigma\n")
    for data in statistics:
        out.write("%s , %s, %s, %s\n" % data)
