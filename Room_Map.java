package room_map;
import java.awt.Color;
import java.awt.Graphics;
import java.util.Random;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class Room_Map extends JPanel{
  static int j;
    /**
     * @param args the command line arguments
     */
  public static void main(String[] args) {
    // TODO code application logic here     
    JFrame f = new JFrame();
    f.setSize(330, 700);
    f.add(new Room_Map());
    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    f.setVisible(true);
    Random rn = new Random();
    while(true) {
      j = (rn.nextInt(4));
      f.repaint();
    }
  
  }

  public void paint (Graphics g) {
    g.setColor(Color.LIGHT_GRAY);
    g.fillRect (10, 10, 300, 650);

    g.setColor(Color.BLUE);
    g.fillRect(40, 40, 240, 200);
    g.fillRect(40, 280, 240, 100);
    g.fillRect(40, 420, 240, 200);

    g.setColor(Color.YELLOW);
    g.fillOval(45, 30, 10, 10);
    g.fillOval(280, 50, 10, 10);
    g.fillOval(260, 380, 10, 10);
    g.fillOval(10, 430, 10, 10);
    
    g.setColor(Color.BLACK);
    g.drawString("BEACON 4", 35, 20);
    g.drawString("BEACON 3", 250, 40);
    g.drawString("BEACON 2", 240, 400);
    g.drawString("BEACON 1", 10, 410);
    g.setColor(Color.RED);

    switch(j) {
      case 0: g.fillRect(280, 40, 30, 340);
              break;
      case 1: g.fillRect(40, 380, 240, 40);
              break;
      case 2:g.fillRect(10, 420, 30, 200);
              break;
      case 3:g.fillRect(40, 10, 240, 30);
              break;
    }
  }
}