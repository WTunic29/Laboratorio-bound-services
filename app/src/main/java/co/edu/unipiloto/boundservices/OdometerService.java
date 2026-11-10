
package co.edu.unipiloto.boundservices;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class OdometerService extends Service implements LocationListener {

    private static final String CHANNEL_ID = "odometer_channel";
    private static final int NOTIFICATION_ID = 1001;

    private final IBinder binder = new OdometerBinder();

    private LocationManager locationManager;
    private DatabaseHelper databaseHelper;

    private double distanciaRecorrida = 0.0;
    private Location ultimaUbicacion;

    private String ubicacionActual = "Ubicación pendiente";

    private boolean registrando = false;

    private long horaInicio;
    private double latitudFinal = 0.0;
    private double longitudFinal = 0.0;

    public class OdometerBinder extends Binder {
        public OdometerService getService() {
            return OdometerService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        databaseHelper = new DatabaseHelper(this);

        crearCanalNotificaciones();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (registrando) {
            iniciarNotificacion();
        }

        return START_STICKY;
    }

    private void crearCanalNotificaciones() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Registro de distancia",
                    NotificationManager.IMPORTANCE_LOW
            );

            channel.setDescription("Notificación del odómetro activo");

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            manager.createNotificationChannel(channel);
        }
    }

    private void iniciarNotificacion() {

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Odómetro activo")
                .setContentText("Registrando ubicación y distancia recorrida")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            );

        } else {

            startForeground(NOTIFICATION_ID, notification);
        }
    }

    public boolean iniciarRegistro(float precision, long intervaloSegundos) {

        if (registrando) {
            return true;
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        try {

            distanciaRecorrida = 0.0;
            ultimaUbicacion = null;

            horaInicio = System.currentTimeMillis();

            latitudFinal = 0.0;
            longitudFinal = 0.0;

            iniciarNotificacion();

            long intervaloMilisegundos = intervaloSegundos * 1000;

            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    intervaloMilisegundos,
                    precision,
                    this
            );

            registrando = true;

            Log.d("ODOMETER", "Registro iniciado en primer plano");

            return true;

        } catch (SecurityException | IllegalArgumentException e) {

            registrando = false;

            stopForeground(STOP_FOREGROUND_REMOVE);

            Log.e("ODOMETER", "Error al iniciar GPS", e);

            return false;
        }
    }

    public void detenerRegistro() {

        if (!registrando) {
            return;
        }

        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }

        registrando = false;

        long horaFin = System.currentTimeMillis();
        long duracion = horaFin - horaInicio;

        String fecha = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(horaInicio));

        long resultado = databaseHelper.guardarRecorrido(
                fecha,
                latitudFinal,
                longitudFinal,
                distanciaRecorrida,
                duracion
        );

        if (resultado != -1) {
            Log.d("ODOMETER", "Recorrido guardado correctamente: " + resultado);
        } else {
            Log.e("ODOMETER", "Error al guardar el recorrido");
        }

        Log.d("ODOMETER", "Distancia final: " + distanciaRecorrida);

        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();

    }

    @Override
    public void onLocationChanged(Location nuevaUbicacion) {

        if (!registrando) {
            return;
        }

        if (ultimaUbicacion != null) {

            float distancia = ultimaUbicacion.distanceTo(nuevaUbicacion);

            if (distancia > 0 && distancia <= 10000) {
                distanciaRecorrida += distancia;
            }

        }

        ultimaUbicacion = nuevaUbicacion;

        latitudFinal = nuevaUbicacion.getLatitude();
        longitudFinal = nuevaUbicacion.getLongitude();

        ubicacionActual = "Latitud: " + latitudFinal
                + "\nLongitud: " + longitudFinal;

        Log.d("ODOMETER", "Distancia acumulada: " + distanciaRecorrida);

    }

    public double getDistance() {
        return distanciaRecorrida;
    }

    public String getUbicacionActual() {
        return ubicacionActual;
    }

    public boolean isRegistrando() {
        return registrando;
    }

    public void reiniciarDistancia() {
        distanciaRecorrida = 0.0;
        ultimaUbicacion = null;
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
        ubicacionActual = "GPS desactivado";
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onDestroy() {

        if (registrando) {
            detenerRegistro();
        }

        super.onDestroy();
    }

}
