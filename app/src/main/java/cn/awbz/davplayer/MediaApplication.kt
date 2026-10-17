package cn.awbz.davplayer

import androidx.multidex.MultiDexApplication
import cn.awbz.davplayer.data.*
import java.util.concurrent.Executors

class MediaApplication : MultiDexApplication() {
    var sessionOrientation: Int? = null
    var activeScreens = 0
    val workers = Executors.newFixedThreadPool(3)
    val storageWorker = Executors.newSingleThreadExecutor()
    lateinit var store: Store
    lateinit var library: Library
    lateinit var detailResources: DetailResources
    override fun onCreate() {
        super.onCreate()
        store = Store(this, storageWorker)
        detailResources = DetailResources(filesDir, storageWorker)
        library = Library(OpenListCatalog(), detailResources.gate)
    }
}
