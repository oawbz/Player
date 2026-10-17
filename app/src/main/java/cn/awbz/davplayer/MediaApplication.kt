package cn.awbz.davplayer

import android.app.Application
import cn.awbz.davplayer.data.*
import java.util.concurrent.Executors

class MediaApplication : Application() {
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
        library = Library(OpenListCatalog())
        detailResources = DetailResources(filesDir, storageWorker)
    }
}
