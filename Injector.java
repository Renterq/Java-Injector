import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import javax.swing.JOptionPane;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class Injector {
    public interface MyKernel32 extends Library {
        MyKernel32 INSTANCE = Native.load("kernel32", MyKernel32.class);
        
        HANDLE OpenProcess(int dwDesiredAccess, boolean bInheritHandle, int dwProcessId);
        Pointer VirtualAllocEx(HANDLE hProcess, Pointer lpAddress, int dwSize, int flAllocationType, int flProtect);
        boolean WriteProcessMemory(HANDLE hProcess, Pointer lpBaseAddress, byte[] lpBuffer, int nSize, IntByReference lpNumberOfBytesWritten);
        Pointer CreateRemoteThread(HANDLE hProcess, Pointer lpThreadAttributes, int dwStackSize, Pointer lpStartAddress, Pointer lpParameter, int dwCreationFlags, Pointer lpThreadId);
        Pointer GetProcAddress(HANDLE hModule, String lpProcName);
        HANDLE GetModuleHandleA(String lpModuleName);
    }

    public static void main(String[] args) {
        try {
            File jarDir = new File(Injector.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
            File dllFile = new File(jarDir, "dllmain.dll");
            
            if (!dllFile.exists()) {
                JOptionPane.showMessageDialog(null, "Hata: 'dllmain.dll' dosyasi bulunamadi!\nLutfen DLL dosyasini bu .jar ile ayni klasore koyun.\nAranan yer: " + dllFile.getAbsolutePath(), "Injector Hatasi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            String dllPath = dllFile.getAbsolutePath();
            
            // Minecraft (javaw.exe) PID'sini bul (ProcessBuilder kullanımı exec(String) deprecated hatasını çözer)
            int targetPid = -1;
            ProcessBuilder pb = new ProcessBuilder("tasklist", "/fi", "imagename eq javaw.exe", "/nh", "/fo", "csv");
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("javaw.exe")) {
                    String[] parts = line.split("\",\"");
                    if (parts.length >= 2) {
                        targetPid = Integer.parseInt(parts[1].replace("\"", ""));
                        break;
                    }
                }
            }

            if (targetPid == -1) {
                JOptionPane.showMessageDialog(null, "Minecraft (javaw.exe) arka planda bulunamadi!\nLutfen once oyunu acin.", "Hata", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Windows API uzerinden isleme mudahale et
            HANDLE hProcess = MyKernel32.INSTANCE.OpenProcess(0x1F0FFF, false, targetPid); // PROCESS_ALL_ACCESS
            if (hProcess == null) {
                JOptionPane.showMessageDialog(null, "Oyuna erisim saglanamadi. (OpenProcess failed)\nYonetici olarak calistirmayi deneyin.", "Hata", JOptionPane.ERROR_MESSAGE);
                return;
            }

            byte[] pathBytes = (dllPath + "\0").getBytes(StandardCharsets.US_ASCII);
            Pointer allocAddr = MyKernel32.INSTANCE.VirtualAllocEx(hProcess, null, pathBytes.length, 0x1000 | 0x2000, 0x40); // MEM_COMMIT | MEM_RESERVE, PAGE_EXECUTE_READWRITE
            
            if (allocAddr == null) {
                JOptionPane.showMessageDialog(null, "Hafiza ayrilamadi. (VirtualAllocEx failed)", "Hata", JOptionPane.ERROR_MESSAGE);
                return;
            }

            IntByReference bytesWritten = new IntByReference(0);
            boolean written = MyKernel32.INSTANCE.WriteProcessMemory(hProcess, allocAddr, pathBytes, pathBytes.length, bytesWritten);
            if (!written) {
                JOptionPane.showMessageDialog(null, "Hafizaya yazilamadi. (WriteProcessMemory failed)", "Hata", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // GetModuleHandleA ve GetProcAddress çakışmalarını gidermek için özel tanımlamalarımızı kullanıyoruz
            HANDLE hKernel32 = MyKernel32.INSTANCE.GetModuleHandleA("kernel32.dll");
            Pointer loadLibraryAddr = MyKernel32.INSTANCE.GetProcAddress(hKernel32, "LoadLibraryA");
            if (loadLibraryAddr == null) {
                JOptionPane.showMessageDialog(null, "LoadLibraryA adresi bulunamadi.", "Hata", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Pointer hThread = MyKernel32.INSTANCE.CreateRemoteThread(hProcess, null, 0, loadLibraryAddr, allocAddr, 0, null);
            if (hThread == null) {
                JOptionPane.showMessageDialog(null, "Enjeksiyon basarisiz oldu. (CreateRemoteThread failed)", "Hata", JOptionPane.ERROR_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(null, "Enjeksiyon Basarili!\nOyundan bip sesini duymalisiniz.", "Basarili", JOptionPane.INFORMATION_MESSAGE);
            }
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Bilinmeyen bir hata olustu:\n" + e.getMessage(), "Hata", JOptionPane.ERROR_MESSAGE);
        }
    }
}
