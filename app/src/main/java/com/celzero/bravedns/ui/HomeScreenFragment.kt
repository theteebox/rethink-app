/*
Copyright 2020 RethinkDNS and its authors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.celzero.bravedns.ui


import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.*
import android.icu.text.CompactDecimalFormat
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.text.TextUtils
import android.text.format.DateUtils
import android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE
import android.text.format.DateUtils.MINUTE_IN_MILLIS
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatSpinner
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.SpinnerArrayAdapter
import com.celzero.bravedns.animation.RippleBackground
import com.celzero.bravedns.data.BraveMode
import com.celzero.bravedns.database.AppDatabase
import com.celzero.bravedns.net.doh.Transaction
import com.celzero.bravedns.service.*
import com.celzero.bravedns.ui.HomeScreenActivity.GlobalVariable.DEBUG
import com.celzero.bravedns.ui.HomeScreenActivity.GlobalVariable.appMode
import com.celzero.bravedns.ui.HomeScreenActivity.GlobalVariable.appStartTime
import com.celzero.bravedns.ui.HomeScreenActivity.GlobalVariable.blockedCount
import com.celzero.bravedns.ui.HomeScreenActivity.GlobalVariable.braveMode
import com.celzero.bravedns.ui.HomeScreenActivity.GlobalVariable.lifeTimeQ
import com.celzero.bravedns.ui.HomeScreenActivity.GlobalVariable.lifeTimeQueries
import com.celzero.bravedns.ui.HomeScreenActivity.GlobalVariable.median50
import com.celzero.bravedns.ui.HomeScreenActivity.GlobalVariable.numUniversalBlock
import com.celzero.bravedns.util.Constants.Companion.LOG_TAG
import com.google.android.material.chip.Chip
import settings.Settings
import java.net.NetworkInterface
import java.net.SocketException
import java.util.*


class HomeScreenFragment : Fragment() {


    private lateinit var appManagerLL: LinearLayout

    private lateinit var firewallLL: LinearLayout

    private lateinit var tileShowFirewallLL: LinearLayout
    private lateinit var tileShowDnsLL: LinearLayout
    private lateinit var tileShowDnsFirewallLL: LinearLayout

    private lateinit var noOfAppsTV: TextView

    private lateinit var braveModeSpinner: AppCompatSpinner
    private lateinit var braveModeInfoIcon: ImageView

    private lateinit var dnsOnOffBtn: AppCompatTextView
    private lateinit var rippleRRLayout: RippleBackground

    private lateinit var tileDLifetimeQueriesTxt: TextView
    private lateinit var tileDmedianTxt: TextView
    private lateinit var tileDtrackersBlockedTxt: TextView

    private lateinit var tileFAppsBlockedTxt: TextView
    private lateinit var tileFCategoryBlockedTxt: TextView
    private lateinit var tileFUniversalBlockedTxt: TextView

    private lateinit var tileDFAppsBlockedTxt: TextView
    private lateinit var tileDFMedianTxt: TextView
    private lateinit var tileDFTrackersBlockedtxt: TextView

    private lateinit var appUpTimeTxt: TextView

    private lateinit var chipConfigureFirewall: Chip
    private lateinit var chipViewLogs: Chip
    private lateinit var chipNetworkMonitor : Chip
    private lateinit var chipSettings : Chip

    private lateinit var protectionDescTxt: TextView
    private lateinit var protectionLevelTxt: TextView

    private val MAIN_CHANNEL_ID = "vpn"

    //Removed code for VPN
    private var isServiceRunning: Boolean = false

    private var REQUEST_CODE_PREPARE_VPN: Int = 100

    companion object {
        //private
        const val DNS_MODE = 0
        const val FIREWALL_MODE = 1
        const val DNS_FIREWALL_MODE = 2
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view: View = inflater.inflate(R.layout.fragment_home_screen, container, false)
        initView(view)
        syncDnsStatus()
        initializeValues()
        initializeClickListeners()

        //Show Tile
        showTileForMode()
        //updateTime()
        updateUptime()
        registerForBroadCastReceivers()
        return view
    }

    /*
        Registering the broadcast receiver for the DNS State and the DNS results returned
     */
    private fun registerForBroadCastReceivers() {
        // Register broadcast receiver
        val intentFilter = IntentFilter(InternalNames.RESULT.name)
        intentFilter.addAction(InternalNames.DNS_STATUS.name)
        //intentFilter.addAction(InternalNames.TRACKER.name)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(messageReceiver, intentFilter)
    }

    /*
        Assign initial values to the view and variables.
     */
    private fun initializeValues() {
        val braveModeList = getAllModes()
        val spinnerAdapter = SpinnerArrayAdapter(requireContext(), braveModeList)

        // Set layout to use when the list of choices appear
        // spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        // Set Adapter to Spinner
        braveModeSpinner.adapter = spinnerAdapter

        braveMode = PersistentState.getBraveMode(requireContext())

        if (braveMode == -1) {
            braveModeSpinner.setSelection(DNS_FIREWALL_MODE)
        }
        else {
            braveModeSpinner.setSelection(braveMode)
        }

        lifeTimeQueries = PersistentState.getNumOfReq(requireContext())
        //var lifeTimeQ : MutableLiveData<Int> = MutableLiveData()
        //val aList = PersistentState.getExcludedPackagesWifi(requireContext())
        //appsBlocked.postValue(aList!!.size)
        blockedCount.postValue(PersistentState.getBlockedReq(requireContext()))
    }


    private fun initView(view: View) {
        //Complete UI component initialization for the view
        dnsOnOffBtn = view.findViewById(R.id.fhs_dns_on_off_btn)

        rippleRRLayout = view.findViewById(R.id.content)

        appManagerLL = view.findViewById(R.id.fhs_ll_app_mgr)

        firewallLL = view.findViewById(R.id.fhs_ll_firewall)
        noOfAppsTV = view.findViewById(R.id.tv_app_installed)

        tileShowFirewallLL = view.findViewById(R.id.fhs_tile_show_firewall)
        tileShowDnsLL = view.findViewById(R.id.fhs_tile_show_dns)
        tileShowDnsFirewallLL = view.findViewById(R.id.fhs_tile_show_dns_firewall)

        tileDLifetimeQueriesTxt = view.findViewById(R.id.fhs_tile_dns_lifetime_txt)
        tileDmedianTxt = view.findViewById(R.id.fhs_tile_dns_median_latency_txt)
        tileDtrackersBlockedTxt = view.findViewById(R.id.fhs_tile_dns_trackers_blocked_txt)

        tileFAppsBlockedTxt = view.findViewById(R.id.fhs_tile_firewall_apps_txt)
        tileFCategoryBlockedTxt = view.findViewById(R.id.fhs_tile_firewall_category_txt)
        tileFUniversalBlockedTxt = view.findViewById(R.id.fhs_tile_firewall_universal_txt)

        tileDFAppsBlockedTxt = view.findViewById(R.id.fhs_tile_dns_firewall_apps_blocked_txt)
        tileDFMedianTxt = view.findViewById(R.id.fhs_tile_dns_firewall_median_latency_txt)
        tileDFTrackersBlockedtxt = view.findViewById(R.id.fhs_tile_dns_firewall_trackers_blocked_txt)

        appUpTimeTxt = view.findViewById(R.id.fhs_app_uptime)

        protectionLevelTxt = view.findViewById(R.id.fhs_protection_level_txt)
        protectionDescTxt = view.findViewById(R.id.fhs_app_connected_desc)

        //Spinner Adapter code
        braveModeSpinner = view.findViewById(R.id.fhs_brave_mode_spinner)
        braveModeInfoIcon = view.findViewById(R.id.fhs_dns_mode_info)
        //For Chip
        chipConfigureFirewall = view.findViewById(R.id.chip_configure_firewall)
        chipViewLogs = view.findViewById(R.id.chip_view_logs)
        chipSettings = view.findViewById(R.id.chip_settings)
        chipNetworkMonitor = view.findViewById(R.id.chip_network_monitor)
    }

    private fun initializeClickListeners() {
    // BraveMode Spinner OnClick
        braveModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                braveMode = DNS_FIREWALL_MODE
            }
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                braveModeSpinner.isEnabled = false
                modifyBraveMode(position)
                showTileForMode()
                object : CountDownTimer(1000, 500) {
                    override fun onTick(millisUntilFinished: Long) {
                        braveModeSpinner.isEnabled = false
                    }
                    override fun onFinish() {
                        braveModeSpinner.isEnabled = true
                    }
                }.start()
            }
        }

        // Brave Mode Information Icon which shows the dialog - explanation
        braveModeInfoIcon.setOnClickListener {
            showDialogForBraveModeInfo()
        }

        //Chips for the Configure Firewall
        chipConfigureFirewall.setOnClickListener {
            startFirewallActivity()
        }

        //Chips for the DNS Screen
        chipViewLogs.setOnClickListener {
            //startQueryListener()
            startConnectionTrackerActivity()
        }

        chipNetworkMonitor.setOnClickListener{
            //startBottomSheetForSettings()
        }

        chipSettings.setOnClickListener{
            /*val threadPoolExecutor = ThreadPoolExecutor(
                2, 2, 0,
                TimeUnit.MILLISECONDS, LinkedBlockingQueue<Runnable>()
            )
            val mainExecutor: Executor = threadPoolExecutor
            val cancellationSignal: CancellationSignal = CancellationSignal()
            val callBack: DnsResolver.Callback<in MutableList<InetAddress>>? = TestCallBack()
            if (callBack != null) {
                DnsResolver.getInstance().query(null, "www.segment.io", 0, mainExecutor, cancellationSignal, callBack)
            }*/
        }

        // Connect/Disconnect button ==> TODO : Change the label to Start and Stop
        dnsOnOffBtn.setOnClickListener(View.OnClickListener {
            dnsOnOffBtn.isEnabled = false
            //TODO : check for the service already running
            val status = VpnController.getInstance()!!.getState(requireContext())
            if (!checkForPrivateDNSandAlwaysON()) {
                if (status!!.activationRequested) {
                    appStartTime = System.currentTimeMillis()
                    dnsOnOffBtn.setText("start")
                    rippleRRLayout.startRippleAnimation()
                    protectionDescTxt.setText(getString(R.string.dns_explanation_disconnected))
                    stopDnsVpnService()
                } else {
                    appStartTime = System.currentTimeMillis()
                    if(DEBUG) Log.d(LOG_TAG,"VPN service start initiated with time $appStartTime")
                    if(VpnController.getInstance()?.getBraveVpnService() != null){
                        VpnController.getInstance()?.stop(requireContext())
                        if(DEBUG) Log.d(LOG_TAG,"VPN service start initiated but the brave service is already running - so stop called")
                    }else{
                        if(DEBUG) Log.d(LOG_TAG,"VPN service start initiated")
                    }
                    prepareAndStartDnsVpn()
                }
            }else if(status?.connectionState == BraveVPNService.State.WORKING){
                Toast.makeText(requireContext(),"Always-on enabled for RethinkDNS", Toast.LENGTH_SHORT).show()
            }
            if(DEBUG) Log.d(LOG_TAG,"${checkForPrivateDNSandAlwaysON()}, ${status?.connectionState} ")

            Handler().postDelayed({ dnsOnOffBtn.isEnabled = true }, 500)

        })

        val mDb = AppDatabase.invoke(requireContext().applicationContext)
        val categoryInfoRepository = mDb.categoryInfoRepository()
        categoryInfoRepository.getAppCategoryForLiveData().observe(viewLifecycleOwner, Observer {
            val list = it.filter { a -> a.isInternetBlocked }
            tileFCategoryBlockedTxt.text = list.size.toString()
        })

        median50.observe(viewLifecycleOwner, Observer {
            tileDmedianTxt.text = median50.value.toString() + "ms"
            tileDFMedianTxt.text = median50.value.toString() + "ms"
        })

        lifeTimeQ.observe(viewLifecycleOwner, Observer {
            val lifeTimeConversion = CompactDecimalFormat.getInstance(Locale.US, CompactDecimalFormat.CompactStyle.SHORT).format(lifeTimeQ.value)
            tileDLifetimeQueriesTxt.text = lifeTimeConversion
        })

         blockedCount.observe(viewLifecycleOwner, Observer {
             val blocked = CompactDecimalFormat.getInstance(Locale.US, CompactDecimalFormat.CompactStyle.SHORT).format(blockedCount.value)
             tileDFTrackersBlockedtxt.text = blocked
             tileDtrackersBlockedTxt.text = blocked
         })

        //val mDb = AppDatabase.invoke(requireContext().applicationContext)
        val appInfoRepository = mDb.appInfoRepository()
        appInfoRepository.getBlockedAppCount().observe(viewLifecycleOwner, Observer {
            tileFAppsBlockedTxt.text = it.toString()
            tileDFAppsBlockedTxt.text = it.toString()
        })

       /* appsBlocked.observe(viewLifecycleOwner, Observer {
            tileFAppsBlockedTxt.text = appsBlocked.value.toString()
            tileDFAppsBlockedTxt.text = appsBlocked.value.toString()
        })*/

        numUniversalBlock.observe(viewLifecycleOwner, Observer {
            tileFUniversalBlockedTxt.text = numUniversalBlock.value.toString()
        })

    }

    private fun startBottomSheetForSettings() {
        val bottomSheetFragment = SettingsBottomSheetFragment()
        val frag = activity as FragmentActivity
        bottomSheetFragment.show(frag.supportFragmentManager, bottomSheetFragment.tag)
        /*val intent = Intent(requireContext(), SettingsActivity::class.java)
        startActivity(intent)*/
    }

    /*private fun startSettingsActivity(){
        val intent = Intent(requireContext(), SettingsFragment::class.java)
        startActivity(intent)
    }*/

    private fun checkForPrivateDNSandAlwaysON() : Boolean {
        val status: VpnState? = VpnController.getInstance()!!.getState(context)
        val alwaysOn = android.provider.Settings.Secure.getString(context?.contentResolver, "always_on_vpn_app")
        if (!TextUtils.isEmpty(alwaysOn)) if (context?.packageName == alwaysOn) {
            val lockDown = android.provider.Settings.Secure.getInt(context?.contentResolver, "always_on_vpn_lockdown", 0)
            if (lockDown != 0 && status?.connectionState != BraveVPNService.State.WORKING) {
                showDialogForAlwaysOn()
                return true
            }
        } else if(status?.connectionState != BraveVPNService.State.WORKING){
            showDialogForAlwaysOn()
            return true
        }

        if (getPrivateDnsMode() == PrivateDnsMode.STRICT) {
            Toast.makeText(context, R.string.private_dns_toast, Toast.LENGTH_SHORT).show()
            return false
        }
        return false
    }

    private fun startConnectionTrackerActivity() {
            val status: VpnState? = VpnController.getInstance()!!.getState(context)
            if((status?.on!!) ) {
                if(braveMode == DNS_FIREWALL_MODE || braveMode == DNS_MODE && (getPrivateDnsMode() != PrivateDnsMode.STRICT)) {
                    val intent = Intent(requireContext(), DNSDetailActivity::class.java)
                    startActivity(intent)
                }else{
                    if(getPrivateDnsMode() == PrivateDnsMode.STRICT){
                        Toast.makeText(context, resources.getText(R.string.private_dns_toast).toString().capitalize(), Toast.LENGTH_SHORT).show()
                    }
                    Toast.makeText(context, resources.getText(R.string.brave_dns_connect_mode_change_connection).toString().capitalize(), Toast.LENGTH_SHORT).show()
                }
            }else{
                Toast.makeText(context, resources.getText(R.string.brave_dns_connect_prompt_firewall).toString().capitalize(), Toast.LENGTH_SHORT).show()
            }
        }

    private fun showDialogForAlwaysOn() {
        val builder = AlertDialog.Builder(requireContext())
        //set title for alert dialog
        builder.setTitle(R.string.always_on_dialog_heading)
        //set message for alert dialog
        builder.setMessage(R.string.always_on_dialog)
        builder.setCancelable(true)
        //performing positive action
        builder.setPositiveButton(R.string.always_on_dialog_positive_btn) { dialogInterface, which ->
            val intent = Intent("android.net.vpn.SETTINGS")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }

        builder.setNegativeButton(R.string.always_on_dialog_negative_btn) { dialogInterface, which ->
        }

        // Create the AlertDialog
        val alertDialog: AlertDialog = builder.create()
        // Set other dialog properties
        alertDialog.setCancelable(false)
        alertDialog.show()

    }


    private fun showTileForMode() {
        if (braveMode == DNS_MODE) {
            tileShowDnsLL.visibility = View.VISIBLE
            tileShowFirewallLL.visibility = View.GONE
            tileShowDnsFirewallLL.visibility = View.GONE
        } else if (braveMode == FIREWALL_MODE) {
            tileShowDnsLL.visibility = View.GONE
            tileShowFirewallLL.visibility = View.VISIBLE
            tileShowDnsFirewallLL.visibility = View.GONE
        } else if (braveMode == DNS_FIREWALL_MODE) {
            tileShowDnsLL.visibility = View.GONE
            tileShowFirewallLL.visibility = View.GONE
            tileShowDnsFirewallLL.visibility = View.VISIBLE
        }
        braveModeSpinner.isEnabled = true

    }

    /*
        Start the querylistener activity for the DNS logs.
     */
    /*private fun startQueryListener() {
        val status: VpnState? = VpnController.getInstance()!!.getState(requireContext())
        if (status!!.activationRequested) {
            if (braveMode == DNS_FIREWALL_MODE || braveMode == DNS_MODE) {
                val intent = Intent(requireContext(), DNSDetailActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(requireContext(), resources.getText(R.string.brave_dns_connect_mode_change_dns).toString().capitalize(), Toast.LENGTH_SHORT).show()
            }
        }else {
            Toast.makeText(requireContext(), resources.getText(R.string.brave_dns_connect_prompt_query).toString().capitalize(), Toast.LENGTH_SHORT).show()
        }
    }*/


    override fun onResume() {
        super.onResume()
        syncDnsStatus()
        updateUptime()
    }


    /*
        Start the Firewall activity from the chip
     */
    private fun startFirewallActivity() {
        val status: VpnState? = VpnController.getInstance()!!.getState(requireContext())

        //if ( status!!.activationRequested) {
         if(status?.on!!){
            if (braveMode == DNS_FIREWALL_MODE || braveMode == FIREWALL_MODE) {
                val intent = Intent(requireContext(), FirewallActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(requireContext(), resources.getText(R.string.brave_dns_connect_mode_change_firewall).toString().capitalize(), Toast.LENGTH_SHORT).show()
            }
        }else {
            Toast.makeText(requireContext(), resources.getText(R.string.brave_dns_connect_prompt_firewall).toString().capitalize(), Toast.LENGTH_SHORT).show()
        }
    }

    //TODO -- Modify the logic for the below.
    private fun modifyBraveMode(position: Int) {
        var firewallMode = -1L
        if (position == 0) {
            firewallMode = Settings.BlockModeNone
            braveMode = DNS_MODE
        } else if (position == 1) {
            firewallMode = if (VERSION.SDK_INT >= VERSION_CODES.O && VERSION.SDK_INT < VERSION_CODES.Q)
                Settings.BlockModeFilterProc
            else
                Settings.BlockModeFilter
            braveMode = FIREWALL_MODE
        } else if (position == 2) {
            firewallMode = if(VERSION.SDK_INT >= VERSION_CODES.O && VERSION.SDK_INT < VERSION_CODES.Q)
                Settings.BlockModeFilterProc
            else
                Settings.BlockModeFilter
            braveMode = DNS_FIREWALL_MODE
        }

        appMode?.setFirewallMode(firewallMode)

        if(braveMode == FIREWALL_MODE){
            appMode?.setDNSMode(Settings.DNSModeNone)
        }else{
            appMode?.setDNSMode(-1)
        }

        //PersistentState.setFirewallMode(requireContext(), firewallMode)
        PersistentState.setBraveMode(requireContext(), braveMode)

        if (VpnController.getInstance()!!.getState(requireContext())!!.activationRequested) {
            //updateBuilder()
            if (braveMode == 0)
                protectionDescTxt.setText(getString(R.string.dns_explanation_dns_connected))
            else if (braveMode == 1)
                protectionDescTxt.setText(getString(R.string.dns_explanation_firewall_connected))
            else
                protectionDescTxt.setText(getString(R.string.dns_explanation_connected))
        }

        braveModeSpinner.isEnabled = true
    }

    /*
        Show the Info dialog for various modes in Brave
     */
    private fun showDialogForBraveModeInfo() {

        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setTitle("BraveDNS Modes")
        dialog.setCanceledOnTouchOutside(true)
        dialog.setContentView(R.layout.dialog_info_custom_layout)
        val okBtn = dialog.findViewById(R.id.info_dialog_cancel_img) as ImageView
        okBtn.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()

    }

    /*
        TODO : The data has been hardcoded need to modify
     */
    @SuppressLint("ResourceType")
    private fun getAllModes(): ArrayList<BraveMode> {
        val braveNames = resources.getStringArray(R.array.brave_dns_mode_names)
        val icons = resources.obtainTypedArray(R.array.brave_dns_mode_icons)
        var braveList = ArrayList<BraveMode>(3)
        var braveModes = BraveMode(icons.getResourceId(0, -1), 0, braveNames[0])
        braveList.add(braveModes)
        braveModes = BraveMode(icons.getResourceId(1, -1), 1, braveNames[1])
        braveList.add(braveModes)
        braveModes = BraveMode(icons.getResourceId(2, -1), 2, braveNames[2])
        braveList.add(braveModes)
        icons.recycle()
        return braveList
    }

    override fun onDestroyView() {
        super.onDestroyView()
        //timerHandler.removeCallbacks(updater!!);
    }


    private val messageReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            //Log.d(LOG_TAG,"messageReceiver Request ${intent.action}")
            if (InternalNames.RESULT.name.equals(intent.action)) {
                updateStatsDisplay(
                    PersistentState.getNumOfReq(context).toLong(),
                    intent.getSerializableExtra(InternalNames.TRANSACTION.name) as Transaction
                )

            } else if (InternalNames.DNS_STATUS.name.equals(intent.action)) {
                syncDnsStatus()
            } /*else if(InternalNames.TRACKER.name.equals((intent.action))){
                //syncIPTrackerData(intent.getSerializableExtra(InternalNames.IPTRACKER.name) as IPDetails)
            }*/
        }
    }

    private fun updateStatsDisplay(numRequests: Long, transaction: Transaction) {
        DNSLogFragment.updateStatsDisplay(numRequests, transaction)
    }

    /*private fun syncIPTrackerData(ipDetails: IPDetails){
        ConnectionTrackerActivity.updateApplication(ipDetails)
    }*/

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(messageReceiver)
        //timerHandler.removeCallbacks(updater!!)
        super.onDestroy()
    }

    private fun updateUptime() {
        val upTime = DateUtils.getRelativeTimeSpanString(appStartTime, System.currentTimeMillis(), MINUTE_IN_MILLIS, FORMAT_ABBREV_RELATIVE)
        appUpTimeTxt.setText("(" + upTime + ")")
    }


    private fun prepareAndStartDnsVpn() {
        if (hasVpnService()) {
            if (prepareVpnService()) {
                dnsOnOffBtn.setText("stop")
                rippleRRLayout.stopRippleAnimation()
                startDnsVpnService()
                if(DEBUG) Log.d(LOG_TAG,"VPN service start initiated - startDnsVpnService()")
            }
        } else {
            Log.e("BraveVPN", "Device does not support system-wide VPN mode.")
        }
    }

    private fun stopDnsVpnService() {
        VpnController.getInstance()!!.stop(context)
    }

    private fun startDnsVpnService() {

        VpnController.getInstance()?.start(requireContext())
        if (braveMode == 0)
            protectionDescTxt.setText(getString(R.string.dns_explanation_dns_connected))
        else if (braveMode == 1)
            protectionDescTxt.setText(getString(R.string.dns_explanation_firewall_connected))
        else
            protectionDescTxt.setText(getString(R.string.dns_explanation_connected))
    }

    // Returns whether the device supports the tunnel VPN service.
    private fun hasVpnService(): Boolean {
        return VERSION.SDK_INT >= VERSION_CODES.ICE_CREAM_SANDWICH
    }

    @Throws(ActivityNotFoundException::class)
    private fun prepareVpnService(): Boolean {
        var prepareVpnIntent: Intent? = null
        prepareVpnIntent = try {
            VpnService.prepare(context)
        } catch (e: NullPointerException) {
            // This exception is not mentioned in the documentation, but it has been encountered by Intra
            // users and also by other developers, e.g. https://stackoverflow.com/questions/45470113.
            Log.e("BraveVPN", "Device does not support system-wide VPN mode.")
            return false
        }
        if (prepareVpnIntent != null) {
            startActivityForResult(prepareVpnIntent, REQUEST_CODE_PREPARE_VPN)
            //TODO - Check the below code
            syncDnsStatus() // Set DNS status to off in case the user does not grant VPN permissions
            return false
        }
        return true
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PREPARE_VPN && resultCode == Activity.RESULT_OK) {
            startDnsVpnService()
        } else {
            stopDnsVpnService()
        }
    }


    // Sets the UI DNS status on/off.
    private fun syncDnsStatus() {

        var status = VpnController.getInstance()!!.getState(requireContext())

        if (status!!.activationRequested || status.connectionState == BraveVPNService.State.WORKING) {
            rippleRRLayout.stopRippleAnimation()
            dnsOnOffBtn.text = "stop"
            if (braveMode == 0)
                protectionDescTxt.setText(getString(R.string.dns_explanation_dns_connected))
            else if (braveMode == 1)
                protectionDescTxt.setText(getString(R.string.dns_explanation_firewall_connected))
            else
                protectionDescTxt.setText(getString(R.string.dns_explanation_connected))
        } else {
            rippleRRLayout.startRippleAnimation()
            dnsOnOffBtn.text = "start"
            protectionDescTxt.setText(getString(R.string.dns_explanation_disconnected))
        }

        // Change status and explanation text
        var statusId: Int = R.string.status_exposed
        val explanationId: Int
        var privateDnsMode: PrivateDnsMode = PrivateDnsMode.NONE
        if (status.activationRequested) {
            if (status.connectionState == null) {
                statusId = R.string.status_waiting
                explanationId = R.string.explanation_offline
            } else if (status.connectionState === BraveVPNService.State.NEW) {
                statusId = R.string.status_starting
                explanationId = R.string.explanation_starting
            } else if (status.connectionState === BraveVPNService.State.WORKING) {
                statusId = R.string.status_protected
                explanationId = R.string.explanation_protected
            } else {
                // status.connectionState == ServerConnection.State.FAILING
                statusId = R.string.status_failing
                explanationId = R.string.explanation_failing
                //changeServerButton.visibility = View.VISIBLE
            }
        } else if (isAnotherVpnActive()) {
            statusId = R.string.status_exposed
            explanationId = R.string.explanation_vpn
        } else {
            privateDnsMode = getPrivateDnsMode()
            if (privateDnsMode == PrivateDnsMode.STRICT) {
                statusId = R.string.status_strict
                explanationId = R.string.explanation_strict
            } else if (privateDnsMode == PrivateDnsMode.UPGRADED) {
                statusId = R.string.status_exposed
                explanationId = R.string.explanation_upgraded
            } else {
                statusId = R.string.status_exposed
                explanationId = R.string.explanation_exposed
            }
        }
        if(DEBUG) Log.d(LOG_TAG,"Status - ${status.activationRequested}, ${status.connectionState}")

        if(status.connectionState == BraveVPNService.State.WORKING){
            statusId = R.string.status_protected
        }

        if(braveMode == 1 && status.activationRequested){
            statusId = R.string.status_protected
        }

        if(appMode?.getDNSType() == 3 && status.activationRequested){
            statusId = R.string.status_protected
        }

        var colorId: Int
        colorId = if (status.on) {
            if (status.connectionState != BraveVPNService.State.FAILING) R.color.positive else R.color.accent_bad
        } else if (privateDnsMode == PrivateDnsMode.STRICT) {
            // If the VPN is off but we're in strict mode, show the status in white.  This isn't a bad
            // state, but Intra isn't helping.
            R.color.indicator
        } else {
            R.color.accent_bad
        }
        if (braveMode == FIREWALL_MODE && status.activationRequested)
            colorId = R.color.positive

        //if (HomeScreenActivity.GlobalVariable.DEBUG) Log.d(LOG_TAG,"Sync - statusId: $statusId, status!!.activationRequested: ${status!!.activationRequested}, status.connectionState: ${status.connectionState}  ")
        val color = ContextCompat.getColor(requireContext(), colorId)
        protectionLevelTxt.setTextColor(color)
        protectionLevelTxt.setText(statusId)

    }

    private fun getPrivateDnsMode(): PrivateDnsMode {
        if (VERSION.SDK_INT < VERSION_CODES.P) {
            // Private DNS was introduced in P.
            return PrivateDnsMode.NONE
        }
        val linkProperties: LinkProperties = getLinkProperties()
            ?: return PrivateDnsMode.NONE
        if (linkProperties.privateDnsServerName != null) {
            return PrivateDnsMode.STRICT
        }
        return if (linkProperties.isPrivateDnsActive) {
            PrivateDnsMode.UPGRADED
        } else PrivateDnsMode.NONE
    }

    private fun getLinkProperties(): LinkProperties? {
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (VERSION.SDK_INT < VERSION_CODES.M) {
            // getActiveNetwork() requires M or later.
            return null
        }
        val activeNetwork = connectivityManager.activeNetwork ?: return null
        return connectivityManager.getLinkProperties(activeNetwork)
    }

    private fun isAnotherVpnActive(): Boolean {
        if (VERSION.SDK_INT >= VERSION_CODES.M) {
            val connectivityManager =
                requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities =
                connectivityManager.getNetworkCapabilities(activeNetwork)
                    ?: // It's not clear when this can happen, but it has occurred for at least one user.
                    return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
        // For pre-M versions, return true if there's any network whose name looks like a VPN.
        try {
            val networkInterfaces =
                NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                val name = networkInterface.name

                if (networkInterface.isUp && name != null &&
                    (name.startsWith("tun") || name.startsWith("pptp") || name.startsWith("l2tp"))
                ) {
                    return true
                }
            }
        } catch (e: SocketException) {
            Log.e(LOG_TAG, e.message, e)
        }
        return false
    }

    enum class PrivateDnsMode {
        NONE,  // The setting is "Off" or "Opportunistic", and the DNS connection is not using TLS.
        UPGRADED,  // The setting is "Opportunistic", and the DNS connection has upgraded to TLS.
        STRICT // The setting is "Strict".
    }


}