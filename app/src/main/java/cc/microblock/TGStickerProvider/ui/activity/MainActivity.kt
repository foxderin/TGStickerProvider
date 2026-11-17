@file:Suppress("SetTextI18n", "Deprecation")

package cc.microblock.TGStickerProvider.ui.activity

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cc.microblock.TGStickerProvider.*
import cc.microblock.TGStickerProvider.databinding.ActivityMainBinding
import cc.microblock.TGStickerProvider.utils.*
import cc.microblock.TGStickerProvider.ui.activity.base.BaseActivity
import com.bumptech.glide.Glide
import cc.microblock.TGStickerProvider.ui.view.SyncProgressDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.log.YLog
import java.io.File
import kotlin.concurrent.thread


data class StickerState(var highQuality: Int, var lowQuality: Int, var all: Int)
data class PreviewInfo(var type: String, var url: String)
data class StickerInfo(
    val name: String,
    val id: String,
    val hash: String,
    val remoteState: StickerState,
    val syncedState: StickerState,
    val all: Int,
    val type: String,
    val preview: PreviewInfo
)

class RecyclerAdapterStickerList(private val act: MainActivity) :
    RecyclerView.Adapter<RecyclerAdapterStickerList.ViewHolder>() {
    var stickerList = listOf<StickerInfo>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.sticker_name)
        val id: TextView = view.findViewById(R.id.sticker_id)
        val syncedAll: TextView = view.findViewById(R.id.syncedAll)
        val syncedStatus: TextView = view.findViewById(R.id.syncedStatus)
        val exportedAll: TextView = view.findViewById(R.id.exportedAll)
        val exportedStatus: TextView = view.findViewById(R.id.exportedStatus)
        val totalAll: TextView = view.findViewById(R.id.totalAll)
        val syncBtn: Button = view.findViewById(R.id.syncBtn)
        val rmBtn: Button = view.findViewById(R.id.rmBtn)
        val sourceQualityBtn: Button = view.findViewById(R.id.sourceQualityBtn)
        val imageView: ShapeableImageView = view.findViewById(R.id.imageView)
        val videoView: VideoView = view.findViewById(R.id.videoView)
        val videoCard: CardView = view.findViewById(R.id.videoCard)
        val unsupported: TextView = view.findViewById(R.id.unsupported)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sticker, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val s = stickerList[position]
        holder.name.text = s.name
        holder.id.text = s.id
        holder.syncedAll.text = s.syncedState.all.toString();
        holder.syncedStatus.text = "低清 ${s.syncedState.lowQuality}\n高清 ${s.syncedState.highQuality}"
        holder.exportedAll.text = s.remoteState.all.toString()
        holder.exportedStatus.text = "低清 ${s.remoteState.lowQuality}\n高清 ${s.remoteState.highQuality}"
        holder.totalAll.text = s.all.toString();

        when (s.type) {
            "unknown", "image/webp" -> {
                Glide.with(act)
                    .load(if(s.preview.type==="webp") s.preview.url else R.drawable.bg_dark_round)
                    .error(ColorDrawable(Color.RED))
                    .into(holder.imageView)
                holder.videoCard.visibility = View.GONE
                holder.imageView.visibility = View.VISIBLE
                holder.videoView.stopPlayback()
                holder.unsupported.visibility = View.GONE
                holder.rmBtn.visibility = View.VISIBLE
                holder.syncBtn.visibility = View.VISIBLE
            }
            "video/webm" -> {
                holder.videoView.setVideoPath(s.preview.url)
                holder.videoView.start()
                holder.videoView.setOnPreparedListener {
                    it.isLooping = true
                    it.setVolume(0f, 0f)
                }
                holder.videoView.setOnErrorListener { mp, what, extra ->
                    YLog.error("VideoView error: $what, $extra")
                    true
                }

                holder.videoCard.visibility = View.VISIBLE
                holder.imageView.visibility = View.GONE
                holder.unsupported.visibility = View.GONE
                holder.rmBtn.visibility = View.VISIBLE
                holder.syncBtn.visibility = View.VISIBLE
            }
            else -> {
                holder.imageView.visibility = View.GONE
                holder.videoCard.visibility = View.GONE
                holder.videoView.stopPlayback()
                holder.rmBtn.visibility = View.VISIBLE
                holder.syncBtn.visibility = View.GONE
                holder.unsupported.visibility = View.VISIBLE
                holder.unsupported.text = "不支持的表情包"
            }
        }

        holder.sourceQualityBtn.text = if (act.sourceQuality(s.id) == SourceQuality.HIGH) "来源：优先高清" else "来源：优先低清"
        holder.sourceQualityBtn.isVisible = s.type in setOf("unknown", "image/webp", "video/webm")
        holder.sourceQualityBtn.setOnClickListener { act.chooseSourceQuality(s) }
        holder.syncBtn.setOnClickListener {
            act.syncStickerPack(s, {
                act.runOnUiThread {
                    Toast.makeText(act, "同步完成", Toast.LENGTH_SHORT).show()
                }
            }, { count, reasons ->
                act.runOnUiThread {
                    MaterialAlertDialogBuilder(act)
                        .setTitle("有 $count 个表情同步失败")
                        .setMessage(reasons)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton("确定", null).show()
                }
            });
        }

        fun deleteDialog(title: String, message: String, icon: Int, action: () -> Unit) {
            MaterialAlertDialogBuilder(act)
                .setTitle(title)
                .setMessage(message)
                .setIcon(icon)
                .setPositiveButton("删除") { _, _ ->
                    val pd = SyncProgressDialog(act)
                    pd.setMessage("正在删除")
                    pd.show()
                    pd.setCancelable(false)
                    Thread {
                        try {
                            BoundedStickerWork.withStorageLock { action() }
                        } catch (error: Exception) {
                            YLog.error("Delete failed", error)
                            act.runOnUiThread {
                                pd.dismiss()
                                if (!act.isFinishing && !act.isDestroyed) MaterialAlertDialogBuilder(act)
                                    .setTitle("删除失败").setMessage(error.message ?: "请稍后重试")
                                    .setPositiveButton("确定", null).show()
                            }
                            return@Thread
                        }
                        act.runOnUiThread {
                            pd.setCancelable(true)
                            pd.setMessage("正在更新列表")
                        }
                        act.updateStickerList()
                        act.runOnUiThread { pd.dismiss() }
                    }.start()
                }
                .setNegativeButton("算了", null).show()
        }

        val dialogDelExported = {
            deleteDialog(
                title = "删除导出缓存 ${s.name}",
                message = "你将删除已缓存的表情包集 ${s.name}，这不会影响已同步的表情包，是否继续？",
                icon = android.R.drawable.ic_menu_delete,
            ) {
                HookExportStore.withLock(File(tgspDataPath)) {
                    check(File("$destDataPath/tgSync_${s.id}").let { !it.exists() || it.deleteRecursively() }) {
                        "无法删除导出缓存"
                    }
                    // Explicit deletion includes legacy metadata shadowed by the stable record.
                    File(stickerDataPath).listFiles().orEmpty().filter {
                        it.isFile && it.name.endsWith(".stickerData.txt.jpg") &&
                            runCatching { it.useLines { lines -> lines.firstOrNull() == s.id } }.getOrDefault(false)
                    }.forEach { check(it.delete()) { "无法删除元数据 ${it.name}" } }
                }
            }
        }

        holder.rmBtn.setOnClickListener {
            // dialog confirm
            if (holder.unsupported.isVisible) {
                dialogDelExported()
            } else {
                deleteDialog(
                    title = "删除已同步的表情包集 ${s.name}",
                    message = "你将删除已同步的表情包集 ${s.name}，这将会删除 ${s.syncedState.all} 个表情包文件，是否继续？",
                    icon = android.R.drawable.ic_dialog_alert,
                ) {
                    File("$realDataPath/tgSync_${s.id}").deleteRecursively()
                }
            }
        }

        holder.rmBtn.setOnLongClickListener {
            dialogDelExported()
            true
        }
        //        holder.hash.text = stickerList[position].hash
    }

    override fun getItemCount(): Int {
        return stickerList.size
    }

}

class MainActivity : BaseActivity<ActivityMainBinding>() {

    override fun inflateBinding(inflater: LayoutInflater) = ActivityMainBinding.inflate(inflater)

    private val storageSettings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            onGainedPermission()
        }
    }
    private val legacyStoragePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onGainedPermission()
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                onGainedPermission()
                return
            }
            Toast.makeText(this, "请授予插件完全文件访问权限，以同步表情包~", Toast.LENGTH_LONG).show()
            storageSettings.launch(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            })
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED) {
            onGainedPermission()
        } else {
            legacyStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun onGainedPermission() {
        Toast.makeText(
            this,
            "权限获取成功，可以同步表情包啦~",
            Toast.LENGTH_LONG
        ).show()

        thread { updateStickerList() }
    }

    fun syncStickerPack(info: StickerInfo, finished: () -> Unit, error: (Int, String) -> Unit) {
        val options = StickerSyncOptions(sourceQuality(info.id),
            if (useApng) AnimationFormat.APNG else AnimationFormat.GIF, useHighQualityGif)
        runOnUiThread {
            val progress = SyncProgressDialog(this).apply {
                setMessage("正在等待同步")
                setCancelable(false)
                show()
            }
            thread {
                val failures = (try {
                    StickerSynchronizer(applicationContext).synchronize(
                        File("$destDataPath/tgSync_${info.id}"), File("$realDataPath/tgSync_${info.id}"),
                        File(stickerDataPath, info.hash), options
                    ) { done, total -> progress.setMessage("正在同步 $done/$total") }
                } catch (exception: Exception) {
                    YLog.error("Error while synchronizing ${info.name}", exception)
                    listOf(exception.message ?: "同步失败")
                }).toMutableList()
                try {
                    BoundedStickerWork.withStorageLock {
                        File(nomediaPath2).apply { parentFile?.mkdirs(); if (!exists()) createNewFile() }
                        updateStickerList()
                    }
                } catch (exception: Exception) {
                    failures += "刷新列表失败：${exception.message}"
                } finally {
                    progress.dismiss()
                    runOnUiThread {
                        // Finish the active pack safely, but do not start more packs/show dialogs on a dead Activity.
                        if (!isFinishing && !isDestroyed) {
                            if (failures.isEmpty()) finished() else error(failures.size, failures.joinToString("\n"))
                        }
                    }
                }
            }
        }
    }

    private val syncPreferences by lazy { getSharedPreferences("sync_options", MODE_PRIVATE) }
    var useApng = false
    fun sourceQuality(id: String) = if (syncPreferences.getBoolean("low_source_$id", false)) SourceQuality.LOW else SourceQuality.HIGH

    fun chooseSourceQuality(info: StickerInfo) {
        MaterialAlertDialogBuilder(this)
            .setTitle("来源画质：${info.name}")
            .setSingleChoiceItems(arrayOf("优先高清（缺少时用低清）", "优先低清（缺少时用高清）"),
                if (sourceQuality(info.id) == SourceQuality.HIGH) 0 else 1) { dialog, which ->
                syncPreferences.edit().putBoolean("low_source_${info.id}", which == 1).apply()
                refreshFreshness()
                onFilterChanged()
                dialog.dismiss()
            }.setNegativeButton("取消", null).show()
    }

    @Volatile private var freshnessGeneration = 0L
    private var currentPacks = emptyMap<String, Boolean>()
    private var batchSyncInProgress = false

    private fun refreshFreshness(list: List<StickerInfo> = stickerList.value.orEmpty()) {
        val generation = ++freshnessGeneration
        currentPacks = emptyMap()
        // Hash only when this filter is used; avoid rescanning every pack between batch items.
        if (!requiresSyncOnly || batchSyncInProgress || list.isEmpty()) return
        val options = list.associate { info -> info.id to StickerSyncOptions(sourceQuality(info.id),
            if (useApng) AnimationFormat.APNG else AnimationFormat.GIF, useHighQualityGif) }
        thread work@ {
            val result = BoundedStickerWork.withStorageLock {
                if (generation != freshnessGeneration) return@withStorageLock null
                val sync = StickerSynchronizer(applicationContext)
                list.associate { info -> info.id to runCatching {
                    sync.isCurrent(File("$destDataPath/tgSync_${info.id}"), File("$realDataPath/tgSync_${info.id}"),
                        File(stickerDataPath, info.hash), options.getValue(info.id))
                }.getOrDefault(false) }
            } ?: return@work
            runOnUiThread {
                if (generation == freshnessGeneration && !isDestroyed) {
                    currentPacks = result
                    onFilterChanged()
                }
            }
        }
    }

    var filter = ""
    var requiresSyncOnly = false
    var useHighQualityGif = false

    fun onFilterChanged(list: List<StickerInfo>? = stickerList.value) {
        if (list == null) return
        (binding.stickerManageView.adapter as RecyclerAdapterStickerList).stickerList = list.filter {
            if (requiresSyncOnly && currentPacks[it.id] == true) return@filter false

            it.name.contains(filter, ignoreCase = true) || it.id.contains(filter, ignoreCase = true)
                    || it.type.contains(filter, ignoreCase = true)
        }
        binding.stickerManageView.adapter?.notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate() {
        refreshModuleStatus()
        requestPermission()
        binding.mainTextVersion.text = getString(R.string.module_version, BuildConfig.VERSION_NAME)
        binding.button2.setOnClickListener {
            val hasStorageAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
            }
            if (!hasStorageAccess) {
                requestPermission()
                return@setOnClickListener
            }
            binding.tips.visibility = View.GONE
            binding.manage.visibility = View.VISIBLE
        }
        binding.requiresSyncOnlySwitch.setOnCheckedChangeListener { _, isChecked ->
            requiresSyncOnly = isChecked
            refreshFreshness()
            onFilterChanged()
        }
        useApng = syncPreferences.getBoolean("apng", false)
        binding.apngSwitch.isChecked = useApng
        binding.gifQualitySwitch.isEnabled = !useApng
        binding.apngSwitch.setOnCheckedChangeListener { _, checked ->
            useApng = checked
            syncPreferences.edit().putBoolean("apng", checked).apply()
            binding.gifQualitySwitch.isEnabled = !checked
            refreshFreshness()
            onFilterChanged()
        }
        binding.licensesBtn.setOnClickListener { showLicenses() }
        binding.gifQualitySwitch.setOnCheckedChangeListener { _, isChecked ->
            useHighQualityGif = isChecked
            refreshFreshness()
            onFilterChanged()
        }
        binding.resetBtn.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("清除已同步的表情包集缓存")
                .setMessage("你将清除导出表情包集的缓存，这不会影响已同步的表情包，是否继续？")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("删除") { _, _ ->
                    val pd = SyncProgressDialog(this).apply {
                        setMessage("正在清除")
                        show()
                        setCancelable(false)
                    }
                    thread(true) {
                        try {
                            BoundedStickerWork.withStorageLock {
                                HookExportStore.withLock(File(tgspDataPath)) {
                                    HookExportStore.clearDirectoryPreservingLock(File(exposedPath))
                                }
                            }
                            this.stickerList.postValue(listOf())
                        } catch (error: Exception) {
                            YLog.error("Clear export cache failed", error)
                            updateStickerList()
                            runOnUiThread {
                                if (!isFinishing && !isDestroyed) MaterialAlertDialogBuilder(this)
                                    .setTitle("清除失败").setMessage(error.message ?: "请稍后重试")
                                    .setPositiveButton("确定", null).show()
                            }
                        } finally { pd.dismiss() }
                    }
                }
                .setNegativeButton("算了", null).show()
        }

        binding.syncAllBtn.setOnClickListener {
            runOnUiThread {
                val syncQueue = stickerList.value?.toList().orEmpty()
                if (syncQueue.isEmpty()) {
                    Toast.makeText(this, "表情包为空", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                batchSyncInProgress = true
                refreshFreshness()
                val pd = SyncProgressDialog(this).apply {
                    setMessage("正在同步")
                    show()
                    setCancelable(false)
                }

                thread(true) {
                    var failCount = 0
                    var failReasons = ""

                    fun syncIndex(index: Int) {
                        if (index >= syncQueue.size) {
                            runOnUiThread {
                                pd.dismiss()
                                batchSyncInProgress = false
                                refreshFreshness()
                                onFilterChanged()
                                if (failCount > 0) {
                                    MaterialAlertDialogBuilder(this)
                                        .setTitle("有 $failCount 个表情同步失败")
                                        .setMessage(failReasons)
                                        .setIcon(android.R.drawable.ic_dialog_alert)
                                        .setPositiveButton("确定", null).show()
                                } else {
                                    Toast.makeText(this, "同步完成", Toast.LENGTH_SHORT).show()
                                }
                            }
                            return
                        }

                        syncStickerPack(syncQueue[index], {
                            syncIndex(index + 1)
                        }, { count, reasons ->
                            failCount += count
                            failReasons += reasons
                            syncIndex(index + 1)
                        })
                    }
                    syncIndex(0);
                }

            }
        }

        binding.refreshBtn.setOnClickListener {
            val pd = SyncProgressDialog(this).apply {
                setMessage("正在更新")
                show()
                setCancelable(false)
            }
            thread(true) {
                updateStickerList()
                pd.dismiss()
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "共刷新${this.stickerList.value?.size ?: 0}个贴纸包",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        binding.searchBar.doOnTextChanged() { text, _, _, _ ->
            filter = text.toString()
            onFilterChanged()
        }

        binding.stickerManageView.layoutManager = LinearLayoutManager(this)
        binding.stickerManageView.adapter = RecyclerAdapterStickerList(this)
        binding.stickerManageView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if(newState == RecyclerView.SCROLL_STATE_IDLE || newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    Glide.with(this@MainActivity).resumeRequests()
                } else {
                    Glide.with(this@MainActivity).pauseRequests()
                }
                super.onScrollStateChanged(recyclerView, newState)
            }
        })
//        try {
//            binding.stickerManageView.javaClass.getDeclaredField("mMaxFlingVelocity").let {
//                it.isAccessible = true
//                it.set(binding.stickerManageView, 1000)
//            }
//        } catch (e: Exception) {
//            YLog.error("Error while setting max fling velocity", e)
//        }

        stickerList.observe(this) { list ->
            runOnUiThread {
                refreshFreshness(list)
                onFilterChanged(list)
            }
        }
    }

    private fun showLicenses() {
        val entries = assets.list("licenses").orEmpty().sorted()
        MaterialAlertDialogBuilder(this).setTitle("第三方组件许可")
            .setItems(entries.toTypedArray()) { _, which ->
                val text = assets.open("licenses/${entries[which]}").bufferedReader().use { it.readText() }
                MaterialAlertDialogBuilder(this).setTitle(entries[which]).setMessage(text)
                    .setPositiveButton("关闭", null).show()
            }.setNegativeButton("关闭", null).show()
    }

    override fun onResume() {
        super.onResume()
        refreshModuleStatus()
    }

    private val stickerList by lazy {
        MutableLiveData<List<StickerInfo>>(listOf())
    }

    fun updateStickerList() {
        if (!File(tgspDataPath).exists()) {
            this.stickerList.postValue(listOf())
            return
        }

        val ignoreIds = try {
            File("$exposedPath/ignore.txt").run {
                if (exists())
                    readLines().filterNot { it.isEmpty() || it.startsWith("#") }
                        .map { it.substringBefore('#').trim() }
                        .toSet()
                else {
                    writeText(
                        """# write the ignored sticker IDs here
                            |# separate with line breaks
                        """.trimMargin()
                    )
                    emptySet()
                }
            }
        } catch (e: Exception) {
            YLog.error("Error while reading ignore.txt", e)
            emptySet()
        }
        YLog.info("ignoreIds: [${ignoreIds.joinToString { "`$it`" }}]")

        val stickerList = mutableListOf<StickerInfo>()

        // Stable pack metadata takes precedence over retained legacy hash-named snapshots.
        // No destructive migration: old clients may still write them; deduplicate by export directory.
        val metadataFiles = File(stickerDataPath).listFiles().orEmpty().filter { it.isFile }
            .sortedWith(compareByDescending<File> { it.name.startsWith("pack_") }
                .thenByDescending { it.lastModified() }.thenBy { it.name })
        for (file in metadataFiles) {
            try {
                val hash = file.name
                if (!file.name.endsWith(".stickerData.txt.jpg")) continue
                val lines = File(file.absolutePath).readLines()
                val id = lines[0]
                require(id.matches(Regex("[A-Za-z0-9_]+"))) { "Invalid sticker directory name" }
                if (id in ignoreIds || stickerList.any { it.id == id }) continue
                val name = lines[1]
                val allSize = lines[2].toInt()
                val type = if(lines.size > 3) lines[3] else "unknown"

                if (stickerList.any { it.hash == hash }) continue

                val remoteState = StickerState(0, 0, 0)
                val syncedState = StickerState(0, 0, 0)

                val remoteIds = mutableSetOf<String>()
                File("$destDataPath/tgSync_$id").listFiles()?.filter {
                    it.isFile && it.extension.lowercase() in setOf("webp", "webm")
                }?.forEach {
                    if (it.name.endsWith("_low.webp")) {
                        remoteState.lowQuality++
                    } else if (it.name.endsWith("_high.webp") || it.name.endsWith("_high.webm")) {
                        remoteState.highQuality++
                    }

                    remoteIds += StickerFileIndex.stickerId(it)
                }

                remoteState.all = remoteIds.size

                File("$realDataPath/tgSync_${id}").listFiles()?.filter {
                    it.isFile && it.extension.lowercase() in setOf("png", "gif", "webp")
                }?.forEach {
                    if (it.name.contains("_low.")) {
                        syncedState.lowQuality++
                    } else if (it.name.contains("_high.")) {
                        syncedState.highQuality++
                    }

                    syncedState.all++
                }

                val files = File("$destDataPath/tgSync_${id}").listFiles().orEmpty().filter {
                    it.isFile && it.extension.lowercase() in setOf("webp", "webm")
                }
                val preview = if (files.isNotEmpty()) {
                    val it = files[0]
                    if (it.extension == "webp") {
                        PreviewInfo("webp", it.absolutePath)
                    } else if(it.extension == "webm") {
                        PreviewInfo("webm", it.absolutePath)
                    } else
                    {
                        PreviewInfo("unknown", "")
                    }
                } else {
                    PreviewInfo("unknown", "")
                }

                stickerList.add(StickerInfo(name, id, hash, remoteState, syncedState, allSize, type, preview))
            } catch (e: Exception) {
                YLog.error("Error while parsing ${file.name}", e)
            }
        }

        // resort by remoteState.all
        stickerList.sortByDescending { it.remoteState.all }

        this.stickerList.postValue(stickerList)
    }

    /**
     * Refresh module status
     *
     * 刷新模块状态
     */
    private fun refreshModuleStatus() {
        binding.mainImgStatus.setImageResource(
            if (YukiHookAPI.Status.isModuleActive) R.mipmap.ic_success
            else R.mipmap.ic_warn
        )
        binding.mainTextStatus.text = getString(
            if (YukiHookAPI.Status.isModuleActive) R.string.module_is_activated
            else R.string.module_not_activated
        )
    }
}