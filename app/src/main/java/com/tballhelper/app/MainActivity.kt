package com.tballhelper.app

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.tballhelper.app.databinding.ActivityMainBinding
import com.tballhelper.app.data.GameConfig
import com.tballhelper.app.data.TemplateManager
import com.tballhelper.app.permission.PermissionHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var templateManager: TemplateManager
    private lateinit var permissionHelper: PermissionHelper
    private var gameList: MutableList<GameConfig> = mutableListOf()
    private var currentGameIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        templateManager = TemplateManager(this)
        permissionHelper = PermissionHelper(this)

        loadGames()
        setupViews()
        updateServiceStatus()
    }
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            saveMediaProjectionData(result.resultCode, result.data!!)
            startOverlayService()
        } else {
            Toast.makeText(this, "需要截屏权限", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        templateManager = TemplateManager(this)
        permissionHelper = PermissionHelper(this)

        loadGames()
        setupViews()
        updateServiceStatus()
    }

    private fun loadGames() {
        gameList = templateManager.loadGames().toMutableList()
        if (gameList.isEmpty()) {
            gameList.add(GameConfig("微信桌球", 1080, 2376, "default_template"))
        }
    }

    private fun setupViews() {
        val gameNames = gameList.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, gameNames)
        binding.spinnerGame.adapter = adapter

        binding.spinnerGame.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentGameIndex = position
                updateTemplatePreview()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.btnStartService.setOnClickListener {
            onStartServiceClicked()
        }

        binding.btnStopService.setOnClickListener {
            onStopServiceClicked()
        }

        binding.btnAddGame.setOnClickListener {
            showAddGameDialog()
        }

        binding.btnCaptureTemplate.setOnClickListener {
            captureTemplate()
        }

        binding.btnManageTemplates.setOnClickListener {
            showTemplateManagementDialog()
        }

        updateTemplatePreview()
    }

    private fun onStartServiceClicked() {
        if (!permissionHelper.hasOverlayPermission()) {
            permissionHelper.requestOverlayPermission { granted ->
                if (granted) {
                    startOverlayService()
                } else {
                    Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            startOverlayService()
        }
    }

    private fun onStopServiceClicked() {
        stopService(Intent(this, OverlayService::class.java))
        updateServiceStatus()
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        startForegroundService(intent)
        updateServiceStatus()
    }

    private fun updateServiceStatus() {
        val isRunning = isServiceRunning(OverlayService::class.java)
        binding.tvStatus.text = "状态: ${if (isRunning) "运行中" else "未运行"}"
        binding.btnStartService.isEnabled = !isRunning
        binding.btnStopService.isEnabled = isRunning
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val services = manager.getRunningServices(Int.MAX_VALUE)
        return services.any { it.service == ComponentName(this, serviceClass) }
    }

    private fun showAddGameDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_game, null)
        val etGameName = dialogView.findViewById<TextInputEditText>(R.id.etGameName)

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_add_game)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val name = etGameName.text?.toString() ?: "新游戏"
                val config = GameConfig(name, 1080, 2376, "template_$name")
                gameList.add(config)
                templateManager.saveGames(gameList)
                loadGames()
                binding.spinnerGame.setSelection(gameList.size - 1)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun captureTemplate() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        Toast.makeText(this, "请在设置中授予截屏权限", Toast.LENGTH_LONG).show()
    }

    private fun showTemplateManagementDialog() {
        val templates = templateManager.listTemplates()
        val names = templates.map { it.name }.toTypedArray()

        if (names.isEmpty()) {
            Toast.makeText(this, "暂无模板", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.btn_manage_templates)
            .setItems(names) { _, which ->
                AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_confirm_delete)
                    .setMessage(R.string.dialog_delete_message)
                    .setPositiveButton(R.string.btn_save) { _, _ ->
                        templateManager.deleteTemplate(templates[which].id)
                        Toast.makeText(this, R.string.msg_template_deleted, Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun updateTemplatePreview() {
        if (currentGameIndex < gameList.size) {
            val game = gameList[currentGameIndex]
            val templateFile = templateManager.getTemplateFile(game.templateId)
            if (templateFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(templateFile.absolutePath)
                binding.ivTemplatePreview.setImageBitmap(bitmap)
            } else {
                binding.ivTemplatePreview.setImageBitmap(null)
            }
        }
    }
}