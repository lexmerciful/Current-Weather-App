package com.lex.currentweatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.google.android.gms.location.*
import com.google.android.material.snackbar.Snackbar
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.lex.currentweatherapp.databinding.ActivityMainBinding
import com.lex.currentweatherapp.models.WeatherResponseData
import com.lex.currentweatherapp.network.WeatherService
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {

    private var binding : ActivityMainBinding? = null

    private lateinit var mFusedLocationClient : FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityMainBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding?.root)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (!isLocationEnabled()){
            binding?.MainLayout?.let { Snackbar.make(it,"Location is turned off", Snackbar.LENGTH_INDEFINITE)
                .setAction("TURN ON"){
                    //Open Application Location Settings
                    try {
                        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        startActivity(intent)
                    }catch (e: ActivityNotFoundException){
                        e.printStackTrace()
                    }
                }}?.show()
        }else{
            locationPermissionsRequest()
        }
        //getLocationWeatherDetails()
    }

    private fun locationPermissionsRequest() {
        Dexter.withActivity(this).withPermissions(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        ).withListener(object : MultiplePermissionsListener {
            override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                if (report!!.areAllPermissionsGranted()){

                    requestLocationData()

                }

                if (report.isAnyPermissionPermanentlyDenied) {
                    Toast.makeText(this@MainActivity,"Location permission has been denied. Please enable location permission!",
                        Toast.LENGTH_SHORT).show()
                }
            }
            override fun onPermissionRationaleShouldBeShown(permissions: MutableList<PermissionRequest>, token: PermissionToken)
            {
                showRationaleDialogForPermission()
            }
        }).onSameThread().check()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        val mLocationRequest = LocationRequest.create()
        mLocationRequest.priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback,
            Looper.myLooper())
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
             val mLastLocation: Location? = locationResult.lastLocation
            val latitude = mLastLocation?.latitude
            Log.i("Current Latitude", latitude.toString())

            val longitude = mLastLocation?.longitude
            Log.i("Current Longitude", longitude.toString())
            getLocationWeatherDetails(latitude!!, longitude!!)
        }
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude:Double){
        if (Constants.isNetworkAvailable(this@MainActivity)){

            //We need to prepare retrofit
            val retrofit: Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            //Create a service based on the retrofit
            val service: WeatherService = retrofit
                .create<WeatherService>(WeatherService::class.java)

            //Prepare the list call based on the service
            val listCall: Call<WeatherResponseData> = service.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            listCall.enqueue(object : Callback<WeatherResponseData>{
                override fun onResponse(
                    call: Call<WeatherResponseData>,
                    response: Response<WeatherResponseData>
                ) {
                    //Check whether response is successful or not
                    if (response!!.isSuccessful){
                        val weatherList: WeatherResponseData? = response.body()
                        Log.i("Response Result: ", "$weatherList")
                    }else{
                        //If response is not successful, we check the response code
                        val responseCode = response.code()
                        when(responseCode){
                            400 -> {
                                Log.e("Error 400", "Bad Connection")
                            }
                            404 -> {
                                Log.e("Error 404", "Not Found")
                            }
                            else -> {
                                Log.e("Error", "Generic Error")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponseData>, t: Throwable) {
                    Log.e("Errorrrr", t!!.message.toString())
                }

            })

        }else{
            binding?.root?.let { Snackbar.make(it, "No Internet Connection", Snackbar.LENGTH_INDEFINITE) }?.show()
        }
    }

    private fun showRationaleDialogForPermission() {
        val dialog = AlertDialog.Builder(this)
        dialog.setMessage("It seems you've declined permission required for this application. It can be enabled in the Application Settings")
        dialog.setPositiveButton("Go To Settings") { _, _ ->
            //Open Application Settings
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                e.printStackTrace()
            }
        }
        dialog.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun isLocationEnabled(): Boolean{
        // This provides access to location service
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}