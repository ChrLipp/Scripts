@Grapes([
    @Grab(group='net.java.dev.jna', module='jna', version='4.1.0'),
    @Grab(group='net.java.dev.jna', module='jna-platform', version='4.1.0')
])

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.platform.win32.Win32Exception

// just trigger the environment settings
def fix = new GvmWindowsFixer()
fix.fixEnvironment()

/**
 * When using GVM  under Cygwin, GVM only sets the Cygwin environment vars correctly.
 * This script sets the Windows environment variables for all installed modules.
 * Call it outside Cygwin.
 */
class GvmWindowsFixer
{
    /** Root dir of Cygwin installation, Windows Style */
    final String cygwinRootDir
    
    /** Home dir of current user, Windows Style */
    final String homeDir
    
    /**
     * Ctor.
     */
    GvmWindowsFixer()
    {
        cygwinRootDir = findCygwinRootDir()
        homeDir = findHomeDir()
    }

    /**
     * Reads Cygwin installation directory from the Registry.
     * This is needed because Cygwin's <bin> directory may not be in the path.
     */
    private String findCygwinRootDir()
    {
        try {
            def cygwinRootDir = Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, 'SOFTWARE\\Cygwin\\setup', 'rootdir')
        }
        catch (Win32Exception e) {
            println 'Error: Cygwin installation not found.'
            throw e
        }
    }
    
    /**
     * Determine Cygwin home directory for current user.
     * The directory is normally located inside the cygwin installation directory, but the user may move the directory.
     * cygpath is a safe method to determine the location.
     */
    private String findHomeDir()
    {
        def homeDir = excuteCygwinCommand('cygpath -w $HOME')
    }
    
    /**
     * Executes a Cygwin command via bash.
     */
    private String excuteCygwinCommand(String command)
    {
        def commandLine = cygwinRootDir + "\\bin\\bash.exe --login -c \"$command\""
        def proc = commandLine.execute()
        proc.waitFor()
        def result = proc.in.text.trim()
    }
    
    /**
     * Lists all the GVM moduls. These are the subdirectories without the gvm directories.
     */
    private File[] getModuleList()
    {
        def gvmRoot = new File("$homeDir\\.gvm")
        def exclude = ['archives', 'bin', 'etc', 'ext', 'src', 'tmp', 'var']
        def result = []
        
        gvmRoot.eachDir { if (!exclude.contains(it.name)) result << it }
        result
    }
    
    /**
     * Converts a Windows path to a Unix path.
     */
    private String winPathToCygwinPath(String winPath)
    {
        excuteCygwinCommand("cygpath -u '$winPath'")
    }
    
    /**
     * Converts a Unix path to a Windows path.
     */
    private String cygwinPathToWinPath(String cygwinPath)
    {
        excuteCygwinCommand("cygpath -w '$cygwinPath'")
    }
    
    /**
     * Sets a Windows environment variable to all GVM modules which are set to default.
     */
    void fixEnvironment()
    {
        // read path value
        def pathValue = getEnvironment('PATH')
        def pathList = pathValue.tokenize(';')

        // read modules with a default entry
        getModuleList().each { fileDir ->
            def current = "$homeDir\\.gvm\\$fileDir.name\\current"
            def envVar = "${fileDir.name.toUpperCase()}_HOME"
            def pathPart = "%${envVar}%\\bin".toString()
            
            if (new File(current).exists())
            {
                def path = cygwinPathToWinPath(winPathToCygwinPath(current))

                println "Setting $envVar to '$path'"
                setEnvironment(envVar, path)
                pathList << pathPart
            }
            else
            {
                pathList -= pathPart
                removeEnvironment(envVar)   
            }
        }
        
        // persist path
        def newPath = pathList.unique().join(';')
        println "Setting PATH to '$newPath'"
        setEnvironment('PATH', newPath)
        println 'Done.'
    }
    
    /**
     * Reads an user spezcific environment variable from the registry.
     */
    String getEnvironment(String key)
    {
         try {
            return Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, 'ENVIRONMENT', key)
        }
        catch (Win32Exception e) {
            return ''
        }
   }
    
    /**
     * Sets an user spezcific environment variable to the registry.
     */
    void setEnvironment(String key, String value)
    {
        Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, 'ENVIRONMENT', key, value)
    }
    
    /**
     * Removes an user spezcific environment variable from the registry.
     */
    void removeEnvironment(String key)
    {
        try {
            Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, 'ENVIRONMENT', key)
        }
        catch (Win32Exception e) {}
   }
}