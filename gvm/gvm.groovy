@Grapes([
    @Grab(group='net.java.dev.jna', module='jna', version='4.1.0'),
    @Grab(group='net.java.dev.jna', module='jna-platform', version='4.1.0')
])

import com.sun.jna.Native
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.DWORDByReference
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.UINT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.platform.win32.Win32Exception
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.W32APIOptions

// just trigger the environment settings
new GvmWindowsFixer().fixEnvironment()

/**
 * When using GVM  under Cygwin, GVM only sets the Cygwin environment vars correctly.
 * This script sets the Windows environment variables for all installed modules.
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
            def cygwinRootDir = Advapi32Util.registryGetStringValue(
                    WinReg.HKEY_LOCAL_MACHINE, 'SOFTWARE\\Cygwin\\setup', 'rootdir')
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
     * Lists all the GVM moduls. These are the subdirectories but without the gvm specific directories.
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
        def origPath = getEnvironment('PATH')
        def pathList = origPath.tokenize(';')

        // read modules with a default entry
        getModuleList().each { fileDir ->
            def current = "$homeDir\\.gvm\\$fileDir.name\\current"
            def envVar = "${fileDir.name.toUpperCase()}_HOME"
            def pathPart = "%${envVar}%\\bin".toString()
            
            if (new File(current).exists()) {
                def path = cygwinPathToWinPath(winPathToCygwinPath(current))

                println "Setting $envVar to '$path'"
                setEnvironment(envVar, path)
                pathList << pathPart
            }
            else {
                pathList -= pathPart
                removeEnvironment(envVar)   
            }
        }
        
        // persist path
        def newPath = pathList.unique().join(';')
        if (newPath != origPath) {
            println "Setting PATH to '$newPath'"
            setEnvironment('PATH', newPath)
            signalPathChange()
        }
        println 'Done.'
    }
    
    /**
     * Reads an user spezcific environment variable from the registry.
     */
    private static String getEnvironment(String key)
    {
         try {
            return Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, 'ENVIRONMENT', key)
        }
        catch (Win32Exception e) {
            return ''
        }
   }
    
    /**
     * Sets an user specific environment variable to the registry.
     */
    private static void setEnvironment(String key, String value)
    {
        if (key == 'PATH') {
            Advapi32Util.registrySetExpandableStringValue(WinReg.HKEY_CURRENT_USER, 'ENVIRONMENT', key, value)
        }
        else {
            Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, 'ENVIRONMENT', key, value)
        }
    }
    
    /**
     * Removes an user specific environment variable from the registry.
     */
    private static void removeEnvironment(String key)
    {
        try {
            Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, 'ENVIRONMENT', key)
        }
        catch (Win32Exception e) {}
   }
   
    /**
     * Broadcasts the changed path so that a reboot is not necessary.
     * However, a open console window must be closed and reopened again.
     */
    private static void signalPathChange()
    {
        MyUser32.INSTANCE.SendMessageTimeout(WinUser.HWND_BROADCAST, MyUser32.WM_SETTINGCHANGE, new WPARAM(0),
                "Environment", MyUser32.SMTO_ABORTIFHUNG, new UINT(5000), new DWORDByReference())
    }
}

/**
 * Extend User32 with function SendMessageTimeout.
 */
interface MyUser32 extends User32
{
    /** The instance. */
    MyUser32 INSTANCE = (MyUser32) Native.loadLibrary("user32", MyUser32.class, W32APIOptions.DEFAULT_OPTIONS)
    
    /**
     * A message that is sent to all top-level windows when the SystemParametersInfo function changes
     * a system-wide setting or when policy settings have changed.
     */
    static final UINT WM_SETTINGCHANGE = new UINT(0x001A)
    
    /**
     * The function returns without waiting for the time-out period to elapse if the receiving thread appears
     * to not respond or "hangs."
     */
    static final UINT SMTO_ABORTIFHUNG = new UINT(0x0002)

    /**
     * Sends the specified message to one or more windows.
     * @param hWnd
     *            A handle to the window whose window procedure will receive the message.
     *            If this parameter is HWND_BROADCAST ((HWND)0xffff), the message is sent to all top-level windows
     *            in the system, including disabled or invisible unowned windows. The function does not return until
     *            each window has timed out. Therefore, the total wait time can be up to the value of uTimeout
     *            multiplied by the number of top-level windows.
     * @param Msg
     *            The message to be sent.
     * @param wParam
     *            Any additional message-specific information.
     * @param lParam
     *            Any additional message-specific information.
     * @param fuFlags
     *            The behavior of this function. This parameter can be one or more of the following values.
     *            SMTO_ABORTIFHUNG (0x0002)         The function returns without waiting for the time-out period
     *                                              to elapse
     *                                              if the receiving thread appears to not respond or "hangs."
     *            SMTO_BLOCK (0x0001)               Prevents the calling thread from processing any other requests
     *                                              until the function returns.
     *            SMTO_NORMAL (0x0000)              The calling thread is not prevented from processing other requests
     *                                              while waiting for the function to return.
     *            SMTO_NOTIMEOUTIFNOTHUNG (0x0008)  The function does not enforce the time-out period as long as the
     *                                              receiving thread is processing messages.
     *            SMTO_ERRORONEXIT (0x0020)         The function should return 0 if the receiving window is destroyed
     *                                              or its owning thread dies while the message is being processed.
     * @param uTimeout
     *            The duration of the time-out period, in milliseconds. If the message is a broadcast message, each
     *            window can use the full time-out period. For example, if you specify a five second time-out period
     *            and there are three top-level windows that fail to process the message, you could have up to a
     *            15 second delay.
     * @param lpdwResult
     *            The result of the message processing. The value of this parameter depends on the message that is
     *            specified.
     */
    LRESULT SendMessageTimeout(HWND hWnd, UINT Msg, WPARAM wParam, String lParam, UINT fuFlags,
                               UINT uTimeout, DWORDByReference lpdwResult)
}