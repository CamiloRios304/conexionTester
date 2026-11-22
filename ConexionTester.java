import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * ConexionTester
 * Aplicación de escritorio (Swing) que permite ingresar la configuración de una
 * base de datos (URL, usuario y contraseña) y probar la conexión mediante JDBC,
 * mostrando en pantalla el resultado o el error técnico producido.
 *
 * Unidad 3 - Desarrollo de interfaces gráficas y conexión a bases de datos.
 * Solo usa la API estándar de Java (sin frameworks externos).
 */
public class ConexionTester extends JFrame {

    private static final Color VERDE = new Color(0, 128, 0);
    private static final Color ROJO = new Color(190, 0, 0);

    private final JTextField txtUrl = new JTextField("jdbc:sqlite:inventario.db", 30);
    private final JTextField txtUsuario = new JTextField(30);
    private final JPasswordField txtClave = new JPasswordField(30);
    private final JButton btnProbar = new JButton("Probar conexión");
    private final JTextArea areaResultado = new JTextArea(10, 40);

    public ConexionTester() {
        super("Probador de conexión JDBC");
        construirInterfaz();
        configurarEventos();
    }

    // ---------------------------------------------------------------
    // 1. Interfaz gráfica
    // ---------------------------------------------------------------
    private void construirInterfaz() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Panel de formulario (GridLayout: etiqueta + campo)
        JPanel panelFormulario = new JPanel(new GridLayout(3, 2, 8, 8));
        panelFormulario.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));
        panelFormulario.add(new JLabel("URL de conexión:"));
        panelFormulario.add(txtUrl);
        panelFormulario.add(new JLabel("Usuario (vacío si es SQLite):"));
        panelFormulario.add(txtUsuario);
        panelFormulario.add(new JLabel("Contraseña (vacía si es SQLite):"));
        panelFormulario.add(txtClave);

        // Panel del botón
        JPanel panelBoton = new JPanel(new FlowLayout(FlowLayout.CENTER));
        panelBoton.add(btnProbar);

        // Panel superior = formulario + botón
        JPanel panelNorte = new JPanel(new BorderLayout());
        panelNorte.add(panelFormulario, BorderLayout.CENTER);
        panelNorte.add(panelBoton, BorderLayout.SOUTH);

        // Área de resultado dentro de un JScrollPane
        areaResultado.setEditable(false);
        areaResultado.setLineWrap(true);
        areaResultado.setWrapStyleWord(true);
        areaResultado.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane scroll = new JScrollPane(areaResultado);
        scroll.setBorder(BorderFactory.createTitledBorder("Resultado de la prueba"));

        add(panelNorte, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null);
    }

    // ---------------------------------------------------------------
    // 2. ActionListener del botón "Probar conexión"
    // ---------------------------------------------------------------
    private void configurarEventos() {
        btnProbar.addActionListener((ActionEvent e) -> probarConexion());
    }

    private void probarConexion() {
        final String url = txtUrl.getText().trim();
        final String usuario = txtUsuario.getText().trim();
        final String clave = new String(txtClave.getPassword());

        if (url.isEmpty()) {
            mostrarResultado("Fallo de validación: debe ingresar la URL de conexión.", ROJO);
            return;
        }

        btnProbar.setEnabled(false);
        mostrarResultado("Probando conexión...", Color.DARK_GRAY);

        // La prueba se ejecuta fuera del hilo de eventos para no congelar la ventana.
        new SwingWorker<Resultado, Void>() {
            @Override
            protected Resultado doInBackground() {
                return intentarConexion(url, usuario, clave);
            }

            @Override
            protected void done() {
                try {
                    Resultado r = get();
                    mostrarResultado(r.mensaje, r.exito ? VERDE : ROJO);
                } catch (Exception ex) {
                    mostrarResultado("Error inesperado: " + ex.getMessage(), ROJO);
                } finally {
                    btnProbar.setEnabled(true);
                }
            }
        }.execute();
    }

    // ---------------------------------------------------------------
    // 3 y 4. Lógica JDBC, excepciones y cierre seguro de recursos
    // ---------------------------------------------------------------
    private Resultado intentarConexion(String url, String usuario, String clave) {
        try {
            // Carga explícita del driver según el motor indicado en la URL.
            String driver = obtenerDriver(url);
            if (driver != null) {
                Class.forName(driver); // puede lanzar ClassNotFoundException
            }

            // try-with-resources: la conexión se cierra siempre, con éxito o con fallo.
            try (Connection conexion = usuario.isEmpty() && clave.isEmpty()
                    ? DriverManager.getConnection(url)
                    : DriverManager.getConnection(url, usuario, clave)) {

                DatabaseMetaData meta = conexion.getMetaData();
                return new Resultado(true,
                        "Conexión exitosa.\n"
                        + "Motor: " + meta.getDatabaseProductName()
                        + " " + meta.getDatabaseProductVersion() + "\n"
                        + "Driver: " + meta.getDriverName()
                        + " " + meta.getDriverVersion());
            }
        } catch (ClassNotFoundException e) {
            return new Resultado(false,
                    "Fallo del driver: no se encontró la clase " + e.getMessage() + ".\n"
                    + "Agregue el .jar del driver JDBC al classpath.");
        } catch (SQLException e) {
            return new Resultado(false,
                    "Fallo de conexión.\n"
                    + "Mensaje técnico: " + e.getMessage() + "\n"
                    + "SQLState: " + e.getSQLState() + " | Código: " + e.getErrorCode());
        }
    }

    /** Devuelve la clase del driver según el prefijo de la URL (null si no se reconoce). */
    private String obtenerDriver(String url) {
        String u = url.toLowerCase();
        if (u.startsWith("jdbc:sqlite:"))     return "org.sqlite.JDBC";
        if (u.startsWith("jdbc:mysql:"))      return "com.mysql.cj.jdbc.Driver";
        if (u.startsWith("jdbc:postgresql:")) return "org.postgresql.Driver";
        if (u.startsWith("jdbc:h2:"))         return "org.h2.Driver";
        if (u.startsWith("jdbc:mariadb:"))    return "org.mariadb.jdbc.Driver";
        if (u.startsWith("jdbc:sqlserver:"))  return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        return null; // DriverManager intentará resolverlo solo
    }

    private void mostrarResultado(String texto, Color color) {
        areaResultado.setForeground(color);
        areaResultado.setText(texto);
        areaResultado.setCaretPosition(0);
    }

    /** Resultado simple de una prueba de conexión. */
    private static class Resultado {
        final boolean exito;
        final String mensaje;

        Resultado(boolean exito, String mensaje) {
            this.exito = exito;
            this.mensaje = mensaje;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ConexionTester().setVisible(true));
    }
}
