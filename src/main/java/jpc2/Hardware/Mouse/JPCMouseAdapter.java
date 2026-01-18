/*
 * Copyright (C) 2017 h0MER247
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package Hardware.Mouse;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.SwingUtilities;



public final class JPCMouseAdapter extends MouseAdapter {
    
    /* ----------------------------------------------------- *
     * Current state of the mouse                            *
     * ----------------------------------------------------- */
    private final boolean[] m_buttons;
    private int m_posX, m_posY;
    private int m_deltaX, m_deltaY, m_deltaWheel;
    private boolean m_hasChangedState;
    
    
    
    public JPCMouseAdapter() {
        
        m_buttons = new boolean[3];
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of MouseAdapter">
    
    @Override
    public void mouseDragged(MouseEvent me) {
     
        if(!me.isConsumed())
            setPosition(me.getX(), me.getY());
    }

    @Override
    public void mouseMoved(MouseEvent me) {
        
        if(!me.isConsumed())
            setPosition(me.getX(), me.getY());
    }
    
    @Override
    public void mousePressed(MouseEvent me) {
        
        if(!me.isConsumed())
            setButtons(me, true);
    }
    
    @Override
    public void mouseReleased(MouseEvent me) {
        
        if(!me.isConsumed())
            setButtons(me, false);
    }
    
    @Override
    public void mouseWheelMoved(MouseWheelEvent mwe) {
        
        if(!mwe.isConsumed()) {
            
            m_deltaWheel += mwe.getWheelRotation();
            m_hasChangedState = true;
        }
    }
    
    private void setPosition(int x, int y) {
        
        m_deltaX += x - m_posX; m_posX = x;
        m_deltaY += y - m_posY; m_posY = y;
        m_hasChangedState = true;
    }
    
    private void setButtons(MouseEvent me, boolean isPressed) {
        
        if(SwingUtilities.isLeftMouseButton(me)) m_buttons[0] = isPressed;
        if(SwingUtilities.isMiddleMouseButton(me)) m_buttons[1] = isPressed;
        if(SwingUtilities.isRightMouseButton(me)) m_buttons[2] = isPressed;
        
        m_hasChangedState = true;
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Methods to return the current state of the mouse">
    
    public void reset() {
        
        m_buttons[0] = m_buttons[1] = m_buttons[2] = false;
        m_posX = m_posY = 0;
        m_deltaX = m_deltaY = m_deltaWheel = 0;
        
        m_hasChangedState = false;
    }
    
    public boolean hasChangedState() {
        
        boolean res = m_hasChangedState;
        m_hasChangedState = false;
        
        return res;
    }
    
    public int getDeltaX(int min, int max, boolean negate) {
        
        int res = Math.min(Math.max(negate ? -m_deltaX : m_deltaX, min), max);
        m_deltaX = 0;
        
        return res;
    }
    
    public int getDeltaY(int min, int max, boolean negate) {
        
        int res = Math.min(Math.max(negate ? -m_deltaY : m_deltaY, min), max);
        m_deltaY = 0;
        
        return res;
    }
    
    public int getDeltaWheel(int min, int max) {
        
        int res = Math.min(Math.max(m_deltaWheel, min), max);
        m_deltaWheel = 0;
        
        return res;
    }
    
    public boolean isLeftButtonPressed() {
        
        return m_buttons[0];
    }
    
    public boolean isMiddleButtonPressed() {
        
        return m_buttons[1];
    }
    
    public boolean isRightButtonPressed() {

        return m_buttons[2];
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Programmatic mouse control for headless operation">

    /**
     * Move the mouse by delta values (for headless/programmatic control)
     */
    public void moveBy(int dx, int dy) {
        m_deltaX += dx;
        m_deltaY += dy;
        m_posX += dx;
        m_posY += dy;
        m_hasChangedState = true;
    }

    /**
     * Set button state directly (for headless/programmatic control)
     * @param button 0=left, 1=middle, 2=right
     * @param pressed true if pressed
     */
    public void setButton(int button, boolean pressed) {
        if (button >= 0 && button < 3) {
            m_buttons[button] = pressed;
            m_hasChangedState = true;
        }
    }

    /**
     * Set mouse wheel delta (for headless/programmatic control)
     */
    public void setWheelDelta(int delta) {
        m_deltaWheel += delta;
        m_hasChangedState = true;
    }

    // </editor-fold>
}
