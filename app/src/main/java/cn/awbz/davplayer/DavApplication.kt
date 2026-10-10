package cn.awbz.davplayer

import androidx.multidex.MultiDexApplication
import cn.awbz.davplayer.data.*
import java.util.concurrent.Executors

class DavApplication : MultiDexApplication() {
    val workers = Executors.newFixedThreadPool(3)
    val storageWorker = Executors.newSingleThreadExecutor()
    lateinit var store: Store
    lateinit var library: Library
    lateinit var posters: Posters
    override fun onCreate() {
        super.onCreate()
        store = Store(this, storageWorker)
        library = Library(DavClient())
        posters = Posters(this, library)
    }
}
