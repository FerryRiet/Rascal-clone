module lang::java::m3::JarTests


import Prelude;
import util::FileSystem;
import lang::java::m3::Core;


public void printAllJarM3(loc jarFolder)
{
    { printJarM3(l[scheme = "jar"][path = l.path + "!"]) | /file(l) <- crawl(jarFolder), l.extension == "jar" };
}

public void printJarM3(loc jarFile)
{
    //iprintToFile(|file:///| + replaceLast(jarFile.path, ".jar!", ".txt"), createM3FromJar(jarFile));
    writeBinaryValueFile(jarFile[scheme = "file"][extension = "bin.m3"], createM3FromJar(jarFile));
}
