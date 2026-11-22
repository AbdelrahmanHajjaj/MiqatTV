package tv.miqat.companion;

import java.io.File;

public final class LgPackageCheck {
    public static void main(String[] args)throws Exception {
        if(args[0].equals("version")) {
            if(LgPackage.compareVersions("0.2.28","0.2.9")<=0 || LgPackage.compareVersions("1.0","1.0.0")!=0 ||
                LgPackage.compareVersions("0.2.1","0.2.28")>=0)throw new AssertionError("Version ordering");
            return;
        }
        boolean accepted;
        try{LgPackage pkg=LgPackage.read(new File(args[1]));accepted=pkg.version.equals("0.2.28");}
        catch(java.io.IOException rejected){accepted=false;}
        if(accepted!=args[0].equals("accept"))throw new AssertionError("Unexpected package acceptance: "+args[1]);
    }
}
