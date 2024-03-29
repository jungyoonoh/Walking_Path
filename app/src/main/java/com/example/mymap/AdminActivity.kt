package com.example.mymap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.firebase.ui.auth.AuthUI
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.ClusterManager
import kotlinx.android.synthetic.main.activity_main.*

class AdminActivity : AppCompatActivity() {

    var backKeyPressedTime:Double = 0.0 // 취소 2번 누르면 종료
    var firstcall = true // 1회 갱신 후 더이상 위치갱신 안하도록
    val myAdminDbHelper: MyAdminDBHelper = MyAdminDBHelper(this) // adminData 전용 DB와 테이블 관리하기위해
    val myDbHelper: MyDBHelper = MyDBHelper(this) // MyData 전용 DB와 테이블 관리
    lateinit var adminData:ArrayList<adminData> // 사용자가 추가한거
    lateinit var data:ArrayList<adminData> // 원래 있던 데이터 클러스터용
    var fusedLocationClient: FusedLocationProviderClient?= null
    var locationCallback: LocationCallback?= null
    var locationRequest: LocationRequest?= null
    lateinit var id:String // 접속한 관리자 아이디
    var loc = LatLng(37.554752,126.970631) // 관리자는 서울역으로 먼저 뜨도록
    lateinit var googleMap: GoogleMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)
        checkTheSetting()
    }

    override fun onRestart() {
        super.onRestart()
        initLocation()
    }

    fun checkTheSetting(){
        init()
        initListener()
        initLocation()
    }

    fun init(){
        val i = intent
        id = i.getStringExtra("ID")
        userId.text="ID : "+id+" (관리자 모드로 작동중)"
        adminData=ArrayList<adminData>()
        data=ArrayList<adminData>()
        adminData=myAdminDbHelper.loadDataAdmin()
        data=myDbHelper.loadAdminData()
    }

    fun initListener(){
        logoutbtn.setOnClickListener {
            AuthUI.getInstance()//로그아웃
                .signOut(this)
                .addOnCompleteListener {
                    val intent = Intent(this, AlarmService::class.java)
                    stopService(intent)
                    val i = Intent(this, StartActivity::class.java)
                    startActivity(i)
                }
        }
    }

    fun initLocation(){
        // 권한정보 체크 = checkSelfPermission
        if(ActivityCompat.checkSelfPermission(this, // 이미 허용되어있다면?
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            getuserlocation()
            startLocationUpdates() // 갱신해주기
            initmap() // 맵정보 초기화
        } else{ // 허용하지 않았다면 or 맨처음 시작이라면?
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),100)
        }
    }

    fun initmap() {
        // map을 초기화를 해줘야..
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync{
            googleMap = it
            googleMap.setMaxZoomPreference(24.0f)
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(loc,18.0f))
            initMapListener()
        }
    }

    fun setCrossWalkMarker(){ // 근처 애들만 마커찍기 테스트용
        // 마커 몇개 이상이면 뭉쳐서 나오게 -> 클러스터(기존 DB)
        var clusterManager = ClusterManager<adminData>(this,googleMap)
        googleMap.setOnCameraChangeListener(clusterManager)

        for(i in 0..data.size - 1) {
            clusterManager.addItem(data[i])
        }

        // 새롭게 유저가 제보한것들
        for(i in 0..adminData.size-1){
            val options = MarkerOptions()
            val sample = LatLng(adminData.get(i).lat.toDouble(), adminData.get(i).long.toDouble())
            options.position(sample)
            options.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            options.title("제보받은정보")
            var geocoder = Geocoder(this)
            var list = geocoder.getFromLocation(adminData.get(i).lat.toDouble(),adminData.get(i).long.toDouble(),1)
            var str = list[0].getAddressLine(0)
            options.snippet(str)
            val mk1 = googleMap.addMarker(options)
            mk1.showInfoWindow()
        }
    }

    fun startLocationUpdates(){ // 현재 위치 받아오기
        locationRequest = LocationRequest.create()?.apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        // 조건을 만족할때 위치정보를 가져오면 이함수 호출
        locationCallback = object : LocationCallback(){
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return
                if(firstcall) {
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(loc,14.0f))
                    setCrossWalkMarker()
                    firstcall = false
                }
            }
        }
        fusedLocationClient?.requestLocationUpdates(
            locationRequest, // 이 주기로
            locationCallback, // 호출시 이 함수가
            Looper.getMainLooper() // 메시지 큐에 전달되는건 이 루퍼에서 처리해준다
        )
    }

    fun getuserlocation(){
        fusedLocationClient=
            LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient?.lastLocation?.addOnSuccessListener {

        }
    }

    fun stopLocationUpdates(){
        fusedLocationClient?.removeLocationUpdates(locationCallback)
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    fun initMapListener(){
        googleMap.setOnMapClickListener {

        }
    }

    override fun onBackPressed() {
        //super.onBackPressed()
        // 2초 이내로 눌러야함
        if(System.currentTimeMillis()>backKeyPressedTime+2000){
            backKeyPressedTime = System.currentTimeMillis().toDouble();
            Toast.makeText(this, "한 번더 누르면 종료합니다.", Toast.LENGTH_SHORT).show();
        }
        //2번째 백버튼 클릭 (종료)
        else{
            appFinish();
        }
    }

    // 앱종료
    fun appFinish(){
        finishAffinity();
        System.runFinalization();
        System.exit(0);
    }

    // 권한
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == 100){
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[1] == PackageManager.PERMISSION_GRANTED){
                getuserlocation()
                startLocationUpdates()
                initmap()
            } else{
                Toast.makeText(this,"위치정보 제공을 하셔야 합니다.",Toast.LENGTH_SHORT).show()
                initmap()
            }
        }
    }
}