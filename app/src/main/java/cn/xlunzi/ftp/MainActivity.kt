package cn.xlunzi.ftp

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.usermanager.PasswordEncryptor
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory
import org.apache.ftpserver.usermanager.impl.BaseUser
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.NetworkInterface
import java.util.*

class MainActivity : Activity() {

    private var hostip: String = "" // 本机IP
    //    private val PORT = 8090
    private val PORT = 2122
    // sd卡目录
    //    private static final String dirname = Environment.getExternalStorageDirectory() + "/ftp";
    private val dirname = "/mnt/sdcard/ftp"
    // ftp服务器配置文件路径
    private val filename = "$dirname/users.properties"
    private var mFtpServer: FtpServer? = null

    private val mHandler = Handler(Handler.Callback { msg ->
        when (msg.what) {
            0x0001 -> Toast.makeText(this@MainActivity, "开启了FTP服务器  ip = $hostip", Toast.LENGTH_SHORT).show()
            0x0002 -> Toast.makeText(this@MainActivity, "关闭了FTP服务器  ip = $hostip", Toast.LENGTH_SHORT).show()
            0x0003 -> Toast.makeText(this@MainActivity, "当前FTP服务已开启 ip = $hostip", Toast.LENGTH_SHORT).show()
            0x0004 -> Toast.makeText(this@MainActivity, "当前FTP服务已关闭 ip = $hostip", Toast.LENGTH_SHORT).show()
        }
        false
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try {
            createDirsFiles()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        btn_start.setOnClickListener {
            Thread(Runnable {
                hostip = getLocalIpAddress()
                println(hostip)
                val text = "ftp://$hostip:2122"
                runOnUiThread {
                    tv_ip.text = text
                }
                if (mFtpServer == null) {
                    startFtpServer(hostip)
                } else {
                    mHandler.sendEmptyMessage(0x0003)
                }
            }).start()
        }

        btn_stop.setOnClickListener {
            Thread(Runnable {
                stopFtpServer()
            }).start()
        }
    }

    /**
     * 创建服务器配置文件
     */
    @Throws(IOException::class)
    private fun createDirsFiles() {
        val dir = File(dirname)
        println(dirname)
        if (!dir.exists()) {
            dir.mkdir()
        }
        var fos: FileOutputStream? = null
        val tmp = getString(R.string.users)
        val sourceFile = File("$dirname/users.properties")
        fos = FileOutputStream(sourceFile)
        fos.write(tmp.toByteArray())
        fos.close()
    }

    /**
     * 开启FTP服务器
     *
     * @param hostIp 本机ip
     */
    private fun startFtpServer(hostIp: String) {
        val serverFactory = FtpServerFactory()
        val userManagerFactory = PropertiesUserManagerFactory()
        val files = File(filename)
        //设置配置文件
        userManagerFactory.file = files
        userManagerFactory.passwordEncryptor = object : PasswordEncryptor {
            override fun encrypt(s: String): String {
                println("xlunzi --> encrypt s = $s")
                return s
            }

            override fun matches(input: String, storedPassword: String): Boolean {
                println("xlunzi --> input--storedPassword = $input--$storedPassword")
                return input == storedPassword
            }
        }
        serverFactory.userManager = userManagerFactory.createUserManager()
        // 设置监听IP和端口号
        val factory = ListenerFactory()
        factory.port = PORT
        factory.serverAddress = hostIp
        serverFactory.addListener("default", factory.createListener())

        try {
            val userManager = serverFactory.userManager
            val baseUser = BaseUser(userManager.getUserByName("admin"))
            baseUser.name = "xlunzi"
            baseUser.password = "1234"
            userManager.save(baseUser)
            println("xlunzi --> baseUser = " + baseUser.name + "--" + baseUser.password)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // start the server
        mFtpServer = serverFactory.createServer()
        try {
            mFtpServer!!.start()
            mHandler.sendEmptyMessage(0x0001)
            println("开启了FTP服务器  ip = $hostIp")
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    /**
     * 关闭FTP服务器
     */
    private fun stopFtpServer() {
        if (mFtpServer != null) {
            mFtpServer!!.stop()
            mFtpServer = null
            mHandler.sendEmptyMessage(0x0002)
            println("关闭了FTP服务器 ip = $hostip")
        } else {
            mHandler.sendEmptyMessage(0x0004)
        }
    }

    /**
     * 获取本机ip
     */
    private fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress.toUpperCase()
                        val isIPv4 = isIpv4(sAddr)
                        if (isIPv4) {
                            return sAddr
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return "192.168.0.0"
    }


    fun onStartServer(view: View) {
        hostip = getLocalIpAddress()
        println("获取本机IP = $hostip")
        startFtpServer(hostip)
    }

    private fun isIpv4(ipv4: String?): Boolean {
        if (ipv4 == null || ipv4.isEmpty()) {
            return false//字符串为空或者空串
        }
        val parts = ipv4.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()//因为java doc里已经说明, split的参数是reg, 即正则表达式, 如果用"|"分割, 则需使用"\\|"
        if (parts.size != 4) {
            return false//分割开的数组根本就不是4个数字
        }
        for (i in parts.indices) {
            try {
                val n = Integer.parseInt(parts[i])
                if (n < 0 || n > 255) {
                    return false//数字不在正确范围内
                }
            } catch (e: NumberFormatException) {
                return false//转换数字不正确
            }

        }
        return true
    }

    override fun onStop() {
        super.onStop()
        
    }
}
