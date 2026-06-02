import os
os.chdir(r"D:\File\软工\课设1\Monopoly-Deal-Cards-Game")
content = open('cp.txt', 'r').read().strip()
# Convert backslashes to forward slashes
content = content.replace('\\', '/')
# Convert semicolons to colons (Unix-style classpath separator)
content = content.replace(';', ':')
cp = 'target/classes:' + content
print(cp)
