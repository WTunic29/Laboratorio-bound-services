
package co.edu.unipiloto.boundservices;

import android.Manifest;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;

import android.location.LocationManager;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import android.database.Cursor;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import androidx.appcompat.app.AppCompatActivity;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private OdometerService odometer;
    private boolean bound = false;
    private boolean serviceBound = false;
    private TextView distanceTextView;
    private TextView locationTextView;
    private EditText precisionEditText;
    private EditText intervalEditText;
    private Button startButton;
    private Button stopButton;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Solicitud de permisos de ubicación.
    private final ActivityResultLauncher<String[]> locationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {

                        Boolean fine = result.get(Manifest.permission.ACCESS_FINE_LOCATION);

                        if (Boolean.TRUE.equals(fine)) {
                            iniciarOdometro();

                        } else {
                            Toast.makeText(this, "Se necesita permiso de ubicación precisa", Toast.LENGTH_LONG).show();
                        }

            });

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        // Continuamos incluso si el usuario rechaza las notificaciones.
                        iniciarOdometro();
                    }
            );

    // Actualización de la interfaz.
    private final Runnable displayDistanceRunnable = new Runnable() {

                @Override
                public void run() {

                    if (bound && odometer != null) {

                        double distancia = odometer.getDistance();

                        distanceTextView.setText(String.format(Locale.getDefault(), "%.1f m", distancia));
                        locationTextView.setText(odometer.getUbicacionActual());

                        handler.postDelayed(this, 1000);

                    }

                }
    };

    // Conexión con el servicio enlazado.
    private final ServiceConnection connection = new ServiceConnection() {

                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {

                    OdometerService.OdometerBinder binder = (OdometerService.OdometerBinder) service;
                    odometer = binder.getService();
                    bound = true;

                    handler.removeCallbacks(displayDistanceRunnable);
                    handler.post(displayDistanceRunnable);

                }

                @Override
                public void onServiceDisconnected(ComponentName name) {

                    bound = false;
                    odometer = null;
                    handler.removeCallbacks(displayDistanceRunnable);

                }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        cargarHistorial();
        cargarReporte();

        distanceTextView = findViewById(R.id.distanceTextView);
        locationTextView = findViewById(R.id.locationTextView);
        precisionEditText = findViewById(R.id.precisionEditText);
        intervalEditText = findViewById(R.id.intervalEditText);
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);

        startButton.setOnClickListener(v -> solicitarPermisos());
        stopButton.setOnClickListener(v -> detenerOdometro());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {

                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;

                });

    }

    private void solicitarPermisos() {

        boolean tienePermiso = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (tienePermiso) {
            solicitarPermisoNotificaciones();

        } else {

            locationPermissionLauncher.launch(new String[]{

                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION

                    });

        }

    }


    private void iniciarOdometro() {

        String precisionTexto = precisionEditText.getText().toString().trim();
        String intervaloTexto = intervalEditText.getText().toString().trim();

        if (precisionTexto.isEmpty() || intervaloTexto.isEmpty()) {
            Toast.makeText(this, "Completa los parámetros de configuración", Toast.LENGTH_SHORT).show();
            return;
        }

        try {

            float precision = Float.parseFloat(precisionTexto);
            long intervalo = Long.parseLong(intervaloTexto);

            if (precision <= 0 || intervalo <= 0) {
                Toast.makeText(this, "Los valores deben ser mayores que cero", Toast.LENGTH_SHORT).show();
                return;
            }

            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(this, "Activa el GPS del dispositivo", Toast.LENGTH_LONG).show();
                return;
            }

            if (!bound || odometer == null) {
                Toast.makeText(this, "El servicio todavía no está conectado", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(this, OdometerService.class);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }

            boolean iniciado = odometer.iniciarRegistro(precision, intervalo);

            if (iniciado) {
                Toast.makeText(this, "Odómetro iniciado", Toast.LENGTH_SHORT).show();

            } else {

                stopService(intent);
                Toast.makeText(this, "No se pudo iniciar el registro", Toast.LENGTH_SHORT).show();

            }

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Introduce valores numéricos válidos", Toast.LENGTH_SHORT).show();
        }

    }


    private void detenerOdometro() {

        if (bound && odometer != null) {

            odometer.detenerRegistro();
            cargarHistorial();
            cargarReporte();

            Toast.makeText(this, "Registro detenido", Toast.LENGTH_SHORT).show();

        }

    }

    @Override
    protected void onStart() {

        super.onStart();

        Intent intent = new Intent(this, OdometerService.class);
        serviceBound = bindService(intent, connection, Context.BIND_AUTO_CREATE);

    }

    @Override
    protected void onStop() {

        super.onStop();
        handler.removeCallbacks(displayDistanceRunnable);

        if (serviceBound) {

            unbindService(connection);
            serviceBound = false;
            bound = false;
            odometer = null;

        }

    }

    private void cargarHistorial() {

        DatabaseHelper dbHelper = new DatabaseHelper(this);
        Cursor cursor = dbHelper.obtenerRecorridos();
        StringBuilder historial = new StringBuilder();

        if (cursor.moveToFirst()) {

            do {

                int id = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ID));
                String fecha = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_FECHA));
                double distancia = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_DISTANCIA));
                long duracion = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_DURACION));

                historial.append("Recorrido #").append(id).append("\n");
                historial.append("Fecha: ").append(fecha).append("\n");
                historial.append("Distancia: ").append(String.format(Locale.getDefault(), "%.2f", distancia)).append(" m\n");
                historial.append("Duración: ").append(duracion / 1000).append(" segundos\n");
                historial.append("------------------------------\n");

            } while (cursor.moveToNext());

        } else {
            historial.append("Todavía no hay recorridos registrados.");
        }

        cursor.close();
        dbHelper.close();

        TextView historialTextView = findViewById(R.id.historialTextView);
        historialTextView.setText(historial.toString());

    }


    private void cargarReporte() {

        DatabaseHelper dbHelper = new DatabaseHelper(this);

        int cantidad = dbHelper.obtenerTotalRecorridos();
        double distanciaTotal = dbHelper.obtenerDistanciaTotal();
        long duracionTotal = dbHelper.obtenerDuracionTotal();

        double promedio = cantidad > 0 ? distanciaTotal / cantidad : 0;

        String reporte = "Total de recorridos: " + cantidad + "\n\n"
                + String.format(Locale.getDefault(),
                "Distancia total: %.2f m\n\n", distanciaTotal)
                + "Duración total: " + (duracionTotal / 1000)
                + " segundos\n\n"
                + String.format(Locale.getDefault(),
                "Promedio por recorrido: %.2f m", promedio);

        TextView reporteTextView = findViewById(R.id.reporteTextView);

        reporteTextView.setText(reporte);
        dbHelper.close();

    }

    private void solicitarPermisoNotificaciones() {

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {

            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);

        } else {
            iniciarOdometro();
        }

    }

}
