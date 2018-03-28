package com.assignment.etho

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.widget.Toast
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.location.*
import com.google.android.gms.location.places.ui.PlaceAutocomplete
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import kotlinx.android.synthetic.main.activity_main.*
import android.util.Log
import android.view.View
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.common.api.ResultCallback
import com.google.android.gms.location.places.AutocompleteFilter
import com.google.android.gms.tasks.Task
import com.mapbox.directions.DirectionsCriteria
import com.mapbox.directions.MapboxDirections
import com.mapbox.directions.service.models.DirectionsResponse
import com.mapbox.directions.service.models.Waypoint
import com.mapbox.mapboxsdk.annotations.Marker
import com.mapbox.mapboxsdk.annotations.Polyline
import com.mapbox.mapboxsdk.annotations.PolylineOptions
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher
import com.mapbox.services.android.navigation.ui.v5.NavigationViewOptions
import retrofit.Callback
import retrofit.Response
import retrofit.Retrofit


class MainActivity : AppCompatActivity() {

    var mpboxMap: MapboxMap? = null
    var desrCoor: LatLng? = null
    var sourceCoor: LatLng? = null
    var locationcallbacks: LocationCallback? = null
    var fusedLocationClient: FusedLocationProviderClient? = null
    var prevPolyLine: Polyline? = null
    var prevLocate: Marker? = null
    var prevmarker: Marker? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        map.onCreate(savedInstanceState)

        checkPermissions()

    }


    fun setUpMap(latLng: LatLng) {
        sourceCoor = latLng
        var camera = CameraPosition.Builder()
                .target(latLng)
                .zoom(10.0)
                .build()
        map.getMapAsync(object : OnMapReadyCallback {
            override fun onMapReady(mapboxMap: MapboxMap?) {
                mpboxMap = mapboxMap
                mapboxMap?.cameraPosition = camera
                if (prevLocate != null)
                    mpboxMap?.removeMarker(prevLocate!!)
                prevLocate = mapboxMap?.addMarker(MarkerOptions().position(latLng))
                clickable_destination.setOnClickListener {
                    try {
                        var typefilter = AutocompleteFilter.Builder()
                                .setCountry("IN")
                                .build()

                        var intent = PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_FULLSCREEN)
                                .setFilter(typefilter)
                                .build(this@MainActivity)


                        startActivityForResult(intent, 101)
                    } catch (e: Throwable) {
                        Toast.makeText(this@MainActivity, "Play services not available", Toast.LENGTH_SHORT).show()

                    } catch (e: GooglePlayServicesNotAvailableException) {
                        Toast.makeText(this@MainActivity, "Play services not available", Toast.LENGTH_SHORT).show()

                    }
                }
            }
        })

    }

    fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    100)
        } else {
            showSettingDialog()

        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {

        if (requestCode == 100) {
            if (grantResults.size > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showSettingDialog()

            } else {

                Toast.makeText(this, "Please grant permissions", Toast.LENGTH_SHORT).show()

            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        loader.visibility = View.VISIBLE
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationcallbacks = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult?) {
                loader.visibility = View.GONE
                setUpMap(LatLng(p0!!.lastLocation.latitude, p0!!.lastLocation.longitude))

            }
        }
        var locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        locationRequest.setInterval(30 * 1000);
        locationRequest.setFastestInterval(5 * 1000)

        fusedLocationClient!!.requestLocationUpdates(locationRequest, locationcallbacks, null)


    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 101) {
            if (resultCode == Activity.RESULT_OK) {
                var place = PlaceAutocomplete.getPlace(this, data)
                destiation_text.setText(place.address)
                desrCoor = LatLng(place.latLng.latitude, place.latLng.longitude)

                if (prevmarker != null)
                    mpboxMap?.removeMarker(prevmarker!!)
                prevmarker = mpboxMap?.addMarker(MarkerOptions().position(LatLng(place.latLng.latitude, place.latLng.longitude)))
                getDirections(sourceCoor, desrCoor)
            } else if (resultCode == PlaceAutocomplete.RESULT_ERROR) {
                Toast.makeText(this, "Error fetching place", Toast.LENGTH_SHORT).show()

            }
        }
        if (requestCode == 102) {
            if (resultCode == Activity.RESULT_OK)
                startLocationUpdates()
            else if (resultCode == Activity.RESULT_CANCELED) {
                retry.visibility = View.VISIBLE
                retry.setOnClickListener {
                    retry.visibility = View.GONE
                    showSettingDialog()
                }
                Toast.makeText(this, "Location should be turned on", Toast.LENGTH_SHORT)
                        .show()
            }
        }
    }

    fun getDirections(source: LatLng?, dest: LatLng?) {
        loader.visibility = View.VISIBLE

        Log.e("POSITION", source.toString() + " " + dest.toString())
        var origin = Waypoint(source!!.longitude, source!!.latitude)
        var dest = Waypoint(dest!!.longitude, dest!!.latitude)

        var client = MapboxDirections.Builder()
                .setAccessToken(resources.getString(R.string.app_token))
                .setOrigin(origin)
                .setDestination(dest)
                .setProfile(DirectionsCriteria.PROFILE_DRIVING)
                .build()
        client.enqueue(object : Callback<DirectionsResponse> {
            override fun onFailure(t: Throwable?) {
                loader.visibility = View.GONE

                Log.e("ERROR", t?.message)
            }

            override fun onResponse(response: Response<DirectionsResponse>?, retrofit: Retrofit?) {
                loader.visibility = View.GONE
                Log.e("result", response?.message())
                nav_button.visibility = View.VISIBLE
                setButton()
                var route = response?.body()?.routes?.get(0)
                Log.e("result", response?.body()?.routes?.size.toString())
                var waypoints = route?.geometry?.waypoints
                var arry = arrayOfNulls<LatLng>(waypoints!!.size)
                for (i in 0..arry.size - 1) {
                    arry[i] = LatLng(waypoints?.get(i)?.latitude!!, waypoints.get(i).longitude)
                }
                if (prevPolyLine != null)
                    mpboxMap?.removePolyline(prevPolyLine!!)
                prevPolyLine = mpboxMap?.addPolyline(PolylineOptions()
                        .addAll(arry!!.toCollection(ArrayList()))
                        .color(resources.getColor(R.color.colorAccent))
                        .width(5.0f))


            }
        })


    }

    fun setButton() {
        nav_button.setOnClickListener {
            var src = com.mapbox.geojson.Point.fromLngLat(sourceCoor!!.longitude, sourceCoor!!.latitude)
            var des = com.mapbox.geojson.Point.fromLngLat(desrCoor!!.longitude, desrCoor!!.latitude)
            var options = NavigationViewOptions.builder()
                    .origin(src)
                    .destination(des)
                    .awsPoolId(null)
                    .shouldSimulateRoute(true)
                    .build()
            NavigationLauncher.startNavigation(this, options)

        }
    }

    fun showSettingDialog() {
        var locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        locationRequest.setInterval(30 * 1000);
        locationRequest.setFastestInterval(5 * 1000)
        var builder = LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);
        builder.setAlwaysShow(true)

        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())
        task.addOnSuccessListener {
            startLocationUpdates()
        }
        task.addOnFailureListener {
            if (it is ResolvableApiException) {
                try {
                    it.startResolutionForResult(this@MainActivity,
                            102)
                } catch (sendEx: IntentSender.SendIntentException) {
                }
            }
        }
    }


    override fun onResume() {
        super.onResume()
        map.onResume()

        if (fusedLocationClient != null)
            startLocationUpdates()


    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        if (fusedLocationClient != null)
            fusedLocationClient!!.removeLocationUpdates(locationcallbacks)


    }

    override fun onStart() {
        super.onStart()
        map.onStart()
    }


}