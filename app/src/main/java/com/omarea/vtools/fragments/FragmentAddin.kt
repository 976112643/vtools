package com.omarea.vtools.fragments

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.SimpleAdapter
import android.widget.TabHost
import android.widget.Toast
import com.omarea.krscripts.action.ActionConfigReader
import com.omarea.krscripts.action.ActionListConfig
import com.omarea.krscripts.switchs.SwitchConfigReader
import com.omarea.krscripts.switchs.SwitchListConfig
import com.omarea.shared.FileWrite
import com.omarea.shell.SysUtils
import com.omarea.shell.units.FlymeUnit
import com.omarea.ui.ProgressBarDialog
import com.omarea.vtools.R
import com.omarea.vtools.activitys.ActivityAddinOnline
import com.omarea.vtools.addin.*
import com.omarea.vtools.dialogs.DialogAddinModifyDPI
import com.omarea.vtools.dialogs.DialogAddinModifydevice
import com.omarea.vtools.dialogs.DialogAddinWIFI
import com.omarea.vtools.dialogs.DialogCustomMAC
import kotlinx.android.synthetic.main.layout_addin.*
import java.io.File
import java.util.*


class FragmentAddin : Fragment() {
    private var myHandler = Handler()
    private fun createItem(title: String, desc: String, runnable: Runnable?, wran: Boolean = true): HashMap<String, Any> {
        val item = HashMap<String, Any>()
        item.put("Title", title)
        item.put("Desc", desc)
        item.put("Wran", wran)
        if (runnable != null)
            item.put("Action", runnable)
        return item
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? = inflater.inflate(R.layout.layout_addin, container, false)

    private fun getIP(): String {
        var r = SysUtils.executeCommandWithOutput(false, "ifconfig wlan0 | grep \"inet addr\" | awk '{ print \$2}' | awk -F: '{print \$2}'")
        if (r == null || r == "") {
            r = "IP"
        }
        return r.trim()
    }

    private fun initAddin(view: View) {
        val listItem = ArrayList<HashMap<String, Any>>().apply {
            add(createItem(getString(R.string.addin_fullscreen_on), getString(R.string.addin_fullscreen_on_desc), Runnable { FullScreenAddin(activity!!).fullScreen() }, false))
            add(createItem(getString(R.string.addin_flyme_static_blur), getString(R.string.addin_flyme_static_blur_desc), Runnable { FlymeUnit().staticBlur() }))
            add(createItem(getString(R.string.addin_miui_hide_search), getString(R.string.addin_miui_hide_search_desc), Runnable { MiuiAddin(context!!).hideSearch() }))

            // add(createItem(getString(R.string.addin_drop_caches), getString(R.string.addin_drop_caches_desc), Runnable { SystemAddin(context!!).dropCache() }))
            add(createItem(getString(R.string.addin_thermal_remove), getString(R.string.addin_thermal_remove_desc), Runnable { ThermalAddin(context!!).showOption() }, false))
            add(createItem(getString(R.string.addin_thermal_remove2), getString(R.string.addin_thermal_remove2_desc), Runnable {
                ThermalAddin(context!!).miuiSetThermalNo()
            }, false))
            // add(createItem(getString(R.string.addin_del_pwd), getString(R.string.addin_del_pwd_desc), Runnable { SystemAddin(context!!).deleteLockPwd() }))
            add(createItem(getString(R.string.addin_wifi), getString(R.string.addin_wifi_desc), Runnable { DialogAddinWIFI(context!!).show() }, false))

            add(createItem(getString(R.string.addin_dpi), getString(R.string.addin_dpi_desc), Runnable { DialogAddinModifyDPI(context!!).modifyDPI(activity!!.windowManager.defaultDisplay, context!!) }, false))
            add(createItem(getString(R.string.addin_deviceinfo), getString(R.string.addin_deviceinfo_desc), Runnable { DialogAddinModifydevice(context!!).modifyDeviceInfo() }, false))
            add(createItem(getString(R.string.addin_mac), getString(R.string.addin_mac_desc), Runnable { DialogCustomMAC(context!!).modifyMAC() }, false))
            add(createItem(getString(R.string.addin_force_dex_compile), getString(R.string.addin_force_dex_compile_desc), Runnable { DexCompileAddin(context!!).run() }, false))
            add(createItem(getString(R.string.addin_pm_dexopt), getString(R.string.addin_pm_dexopt_desc), Runnable { DexCompileAddin(context!!).modifyConfig() }, false))
            add(createItem(getString(R.string.addin_bpc), getString(R.string.addin_bpc_desc), Runnable { PerfBoostConfigAddin(context!!).install() }))
        }

        val mSimpleAdapter = SimpleAdapter(
                view.context, listItem,
                R.layout.action_row_item,
                arrayOf("Title", "Desc"),
                intArrayOf(R.id.Title, R.id.Desc)
        )
        addin_system_listview.adapter = mSimpleAdapter
        addin_system_listview.onItemClickListener = onActionClick
    }

    private var onActionClick = AdapterView.OnItemClickListener { parent, _, position, _ ->
        val item = parent.adapter.getItem(position) as HashMap<*, *>
        if (item.get("Wran") == false) {
            (item["Action"] as Runnable).run()
        } else {
            val dialog = AlertDialog.Builder(context!!)
                    .setTitle(getString(R.string.addin_execute))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.yes) { _, _ ->
                        (item["Action"] as Runnable).run()
                    }
                    .setMessage(item["Title"].toString() + "：" + item["Desc"])
                    .create()
            dialog.window!!.setWindowAnimations(R.style.windowAnim)
            dialog.show()
        }
    }

    private var customConfigLoaded = false
    private fun loadConfig() {
        if (customConfigLoaded)
            return
        val progressBarDialog = ProgressBarDialog(context!!)
        progressBarDialog.showDialog("读取配置，稍等...")
        Thread(Runnable {
            val actions = ActionConfigReader.readActionConfigXml(this.activity!!)
            if (actions == null) {
                myHandler.post {
                    progressBarDialog.hideDialog()
                }
                return@Runnable
            }
            val onlineAddinDir = File(FileWrite.getPrivateFileDir(this.context!!) + "online-addin/")
            if (onlineAddinDir.exists() && onlineAddinDir.isDirectory) {
                val onlineAddins = onlineAddinDir.list { dir, name ->
                    name.endsWith(".xml")
                }
                for (addinFile in onlineAddins) {
                    try {
                        val result = ActionConfigReader.readActionConfigXml(this.activity!!, File("$onlineAddinDir/$addinFile").inputStream())
                        if (result != null && result.size > 0)
                            actions.addAll(result)
                    } catch (ex: Exception) {
                        myHandler.post {
                            Toast.makeText(context, addinFile + "读取解析失败\n" + ex.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            myHandler.post {
                ActionListConfig(this.activity!!).setListData(actions);
                SwitchListConfig(this.activity!!).setListData(SwitchConfigReader.readActionConfigXml(this.activity!!));
                customConfigLoaded = true
                progressBarDialog.hideDialog()
            }
        }).start()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tabHost = view.findViewById(R.id.addin_tabhost) as TabHost
        tabHost.setup()
        tabHost.addTab(tabHost.newTabSpec("tab_1").setContent(R.id.tab0).setIndicator("", context!!.getDrawable(R.drawable.android)))
        tabHost.addTab(tabHost.newTabSpec("tab_2").setContent(R.id.tab1).setIndicator("", context!!.getDrawable(R.drawable.shell)))
        tabHost.addTab(tabHost.newTabSpec("tab_3").setContent(R.id.tab2).setIndicator("", context!!.getDrawable(R.drawable.switchs)))
        tabHost.currentTab = 0
        tabHost.setOnTabChangedListener({ tabId ->
            if (tabId != "tab_1") {
                loadConfig()
            }
        })

        initAddin(view)
        addin_online.setOnClickListener {
            val intent = Intent(context, ActivityAddinOnline::class.java)
            startActivity(intent)
        }
    }

    companion object {
        fun createPage(): Fragment {
            val fragment = FragmentAddin()
            return fragment
        }
    }
}
