module lang::java::m3::JarTests


import Prelude;
import util::FileSystem;
import lang::java::m3::Core;


//Batch print methods
public void printAllJarM3(loc jarFolder)
{
    printAllJarM3(jarFolder, printJarM3);
}

public void printAllJarM3bin(loc jarFolder)
{
    printAllJarM3(jarFolder, printJarM3bin);
}

public void printAllJarM3txt(loc jarFolder)
{
    printAllJarM3(jarFolder, printJarM3txt);
}

private void printAllJarM3(loc jarFolder, void (loc) printer)
{
    for(/file(l) <- crawl(jarFolder), l.extension == "jar", bprintln(l))
    {
        printer(l[scheme = "jar"][path = l.path + "!"]);
    }
}

//Print methods
public void printJarM3(loc jarFile)
{
    M3 model = createM3FromJar(jarFile);
    printJarM3bin(jarFile, model);
    printJarM3txt(jarFile, model);
}

public void printJarM3bin(loc jarFile)
{
    printJarM3bin(jarFile, createM3FromJar(jarFile));
}

public void printJarM3txt(loc jarFile)
{
    printJarM3txt(jarFile, createM3FromJar(jarFile));
}

private void printJarM3bin(loc jarFile, M3 model)
{
    writeBinaryValueFile(jarFile[scheme = "file"][extension = "bin.m3"], model);
}

private void printJarM3txt(loc jarFile, M3 model)
{
    iprintToFile(jarFile[scheme = "file"][extension = "txt"], model);
}
