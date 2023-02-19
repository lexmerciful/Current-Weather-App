package com.lex.currentweatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.Window
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
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
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener, View.OnClickListener {

    private var binding : ActivityMainBinding? = null
    private lateinit var customProgressDialog: Dialog

    private var longitude : Double? = null
    private var latitude : Double? = null

    private var tts : TextToSpeech? = null
    private lateinit var weathertts : WeatherResponseData

    private lateinit var mFusedLocationClient : FusedLocationProviderClient

    private lateinit var mSharedPreferences: SharedPreferences

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityMainBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding?.root)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        setupUI()

        //Text-To-Speech
        tts = TextToSpeech(this,this)

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
            latitude = mLastLocation?.latitude
            Log.i("Current Latitude", latitude.toString())

            longitude = mLastLocation?.longitude
            Log.i("Current Longitude", longitude.toString())
            getLocationWeatherDetails()
        }
    }

    private fun getLocationWeatherDetails(){
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
                latitude!!, longitude!!, Constants.METRIC_UNIT, Constants.APP_ID
            )

            showProgressDialog()

            //ENQUEUE
            listCall.enqueue(object : Callback<WeatherResponseData>{
                @RequiresApi(Build.VERSION_CODES.N)
                override fun onResponse(
                    call: Call<WeatherResponseData>,
                    response: Response<WeatherResponseData>
                ) {
                    //Check whether response is successful or not
                    if (response!!.isSuccessful){
                        cancelProgressDialog()

                        val weatherList: WeatherResponseData? = response.body()

                        //Passing WeatherResponseData to String so as to store in SharedPreference
                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()

                        setupUI()

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
                    cancelProgressDialog()

                    Log.e("Errorrrr", t!!.message.toString())
                }

            })

        }else{
            binding?.root?.let { Snackbar.make(it, "No Internet Connection", Snackbar.LENGTH_LONG)
                .setAction("TRY AGAIN"){
                    getLocationWeatherDetails()
                }}?.show()
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

    private fun showProgressDialog(){
        customProgressDialog = Dialog(this@MainActivity)
        customProgressDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        customProgressDialog.setContentView(R.layout.dialog_custom_progress)
        customProgressDialog.getWindow()?.setBackgroundDrawableResource(R.color.transparent);
        customProgressDialog.show()
        customProgressDialog.setCancelable(false)
    }
    private fun cancelProgressDialog(){
        customProgressDialog.dismiss()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun setupUI(){

        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")

        if (!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponseData::class.java)


            for (i in weatherList.weather.indices){
                Log.i("Weather Name: ", weatherList.weather.toString())

                binding?.tvMain?.text = weatherList.weather[i].main
                binding?.tvMainDescription?.text = weatherList.weather[i].description

                binding?.tvTemp?.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
                binding?.tvHumidity?.text = weatherList.main.humidity.toString() + " per cent"

                binding?.tvMin?.text = weatherList.main.temp_min.toString() + " min"
                binding?.tvMax?.text = weatherList.main.temp_max.toString() + " max"

                binding?.tvSpeed?.text = weatherList.wind.speed.toString()

                binding?.tvName?.text = weatherList.name
                binding?.tvCountry?.text = weatherList.sys.country

                binding?.tvSunriseTime?.text = unixTime(weatherList.sys.sunrise)
                binding?.tvSunsetTime?.text = unixTime(weatherList.sys.sunset)

                sunsetSunriseColorTint(weatherList)

                when(weatherList.weather[i].icon){
                    "01d" -> binding?.ivMain?.setImageResource(R.drawable.sunny)
                    "02d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "03d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "04d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "04n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "10d" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                    "11d" -> binding?.ivMain?.setImageResource(R.drawable.storm)
                    "13d" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)
                    "01n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "02n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "03n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "10n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "11n" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                    "13n" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)
                }
                weathertts = weatherList
                binding?.cvWeatherCondition?.setOnClickListener(this)
                binding?.cvTemperature?.setOnClickListener(this)
                binding?.cvMinMax?.setOnClickListener(this)
                binding?.cvWind?.setOnClickListener(this)
                binding?.cvSunriseSunset?.setOnClickListener(this)
            }
        }
    }

    private fun getUnit(value: String): String {
        Log.i("unitttt", value)
        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value) {
            value = "°F"
        }
        return value
    }

    private fun unixTime(timex: Long): String?{
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh ->{
                getLocationWeatherDetails()
                true
            }else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun sunsetSunriseColorTint(weatherList: WeatherResponseData){
        val date = System.currentTimeMillis()/1000
        Log.e("TIME: ", date.toString())
        if (date < weatherList.sys.sunset){
            binding?.ivSunrise?.setColorFilter(ContextCompat.getColor(this, R.color.image_tint_red),
                android.graphics.PorterDuff.Mode.MULTIPLY)
        }else{
            binding?.ivSunset?.setColorFilter(ContextCompat.getColor(this, R.color.image_tint_red),
                android.graphics.PorterDuff.Mode.MULTIPLY)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS){
            val result = tts!!.setLanguage(Locale.ENGLISH)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED){
                Log.e("TTS", "Language specified not supported")
            }else{
                Log.e("TTS", "Initialization Failed!")
            }
        }
    }

    private fun speakText(text: String){
        tts?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"")
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onClick(view: View?) {
        when(view){
            binding?.cvWeatherCondition ->{speakText("The weather condition is ${weathertts.weather[0].description}")}
            binding?.cvTemperature ->{
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    speakText("The weather temperature is ${weathertts.main.temp}" + getUnit(application.resources.configuration.locales.toString())
                            + " with humidity of ${weathertts.main.humidity} per cent") }}
            binding?.cvMinMax ->{
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                speakText("The minimum temperature is ${weathertts.main.temp_min}" + getUnit(application.resources.configuration.locales.toString())
            + " and the maximum is ${weathertts.main.temp_max}" + getUnit(application.resources.configuration.locales.toString()))}}
            binding?.cvWind ->{speakText("The current wind speed is ${weathertts.wind.speed} miles per hour")}
            binding?.cvSunriseSunset -> {speakText("The sun rises at ${binding?.tvSunriseTime?.text} while the sun sets at ${binding?.tvSunsetTime?.text}")}
        }
    }

}