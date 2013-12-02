module lang::java::m3::JarTests


import Prelude;
import util::FileSystem;
import lang::java::m3::Core;


public void printAllJarM3(loc jarFolder) =
    [printJarM3(|jar:///| + jarFile.path + "!") | jarFile <- crawl(jarFolder, "jar")/*, bprintln(jarFile)*/];

public void printJarM3(loc jarFile) =
    //iprintToFile(|file:///| + replaceLast(jarFile.path, ".jar!", ".txt"), createM3FromJar(jarFile));
    writeBinaryValueFile(|file:///| + replaceLast(jarFile.path, ".jar!", ".bin.m3"), createM3FromJar(jarFile));
