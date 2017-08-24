/*
 * @(#)Transmitter.java	1.12 98/06/25 SMI
 *
 * Author: Tom Corson
 *
 * Copyright (c) 1998 Sun Microsystems, Inc. All Rights Reserved.
 *
 * Sun grants you ("Licensee") a non-exclusive, royalty free, license 
 * to use, modify and redistribute this software in source and binary
 * code form, provided that i) this copyright notice and license appear
 * on all copies of the software; and ii) Licensee does not utilize the
 * software in a manner which is disparaging to Sun.
 *
 * This software is provided "AS IS," without a warranty of any kind.
 * ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES,
 * INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN AND
 * ITS LICENSORS SHALL NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY
 * LICENSEE AS A RESULT OF USING, MODIFYING OR DISTRIBUTING THE
 * SOFTWARE OR ITS DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS
 * BE LIABLE FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT,
 * INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES,
 * HOWEVER CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING
 * OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN IF SUN HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * This software is not designed or intended for use in on-line control
 * of aircraft, air traffic, aircraft navigation or aircraft
 * communications; or in the design, construction, operation or
 * maintenance of any nuclear facility. Licensee represents and
 * warrants that it will not use or redistribute the Software for such
 * purposes.
 */

import java.lang.Thread;

import java.io.IOException;

import java.awt.Panel;
import java.awt.Label;
import java.awt.TextArea;
import java.awt.Checkbox;
import java.awt.Color;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.TextListener;
import java.awt.event.TextEvent;
import java.awt.event.ItemListener;
import java.awt.event.ItemEvent;
import java.nio.file.Files;
import java.io.BufferedReader;
import java.awt.Button;

import javax.swing.JFileChooser;
import javax.swing.Timer;
import javax.comm.SerialPort;

public class Transmitter extends Panel implements TextListener, ItemListener, ActionListener, Runnable
{
	private Panel             p;
	private Panel             p1;
	private Panel             p2;
	private Panel             useProtocolPanel;
	private Panel             timerPanel;
	private Label             timerLabel;
	private TextArea          text;
	private Checkbox          auto;
	private Checkbox          sendBreak;
	private Checkbox          useProtocolBox;
	private Button            runProtocolButton;
	private ByteStatistics    counter;
	private SerialPortDisplay owner;
	private	Thread            thr;
	private Color             onColor;
	private Color             offColor;
	private boolean           first;
	private boolean           modemMode;
	private Timer             timer;
	private int               counterValue;
	static  String            testString = "ABCDEFGHIJKLMNOPQRSTUVWXYZ\nabcdefghijklmnopqrstuvwxyz\n1234567890\n";		

	public Transmitter(SerialPortDisplay	owner,
			   int			rows,
			   int			cols)
	{
		super(new BorderLayout());

		this.first = true;
		this.modemMode = false;

		this.owner = owner;

		p  = new Panel(new FlowLayout());

		p1 = new Panel(new BorderLayout());
		p1.add("West", new Label("Auto Transmit"));
		auto = new Checkbox();
		auto.addItemListener(this);
		p1.add("East", auto);
		p.add(p1);

		p2 = new Panel(new BorderLayout());
		p2.add("West", new Label("Send Break"));
		sendBreak = new Checkbox();
		sendBreak.addItemListener(this);
		p2.add("East", sendBreak);
		p.add(p2);

		useProtocolPanel = new Panel(new BorderLayout());
		useProtocolPanel.add("West", new Label("Use Protocol"));
		useProtocolBox = new Checkbox();
		useProtocolBox.addItemListener(this);
		useProtocolPanel.add("East", useProtocolBox);
		p.add(useProtocolPanel);

		timerPanel = new Panel(new BorderLayout());
		timerLabel = new Label("Next: 0");
		timerPanel.add("West", timerLabel);
		p.add(timerPanel);

		runProtocolButton = new Button();
		runProtocolButton.setLabel("Run Protocol");
		runProtocolButton.addActionListener(this);
		p.add(runProtocolButton);

		this.add("North", p);

		this.text = new TextArea(rows, cols);
		this.text.append("Type here");
		this.text.addTextListener(this);
		this.add("Center", text);

		this.counter = new ByteStatistics("Bytes Sent", 10, 
						  owner.port, false);
		this.add("South", this.counter);

		this.thr = null;

		this.onColor = Color.green;
		this.offColor = Color.black;
		
		this.timer = new Timer(1000, this);
	}

	public Transmitter(SerialPortDisplay	owner,
			   int			rows,
			   int			cols,
			   boolean		modemMode)
	{
		this(owner, rows, cols);

		this.modemMode = modemMode;
	}

	public void setPort(SerialPort	port)
	{
		this.counter.setPort(port);
	}

	public void showValues()
	{
		this.counter.showValues();
	}

	public void clearValues()
	{
		this.counter.clearValues();
	}

	public void setBitsPerCharacter(int	val)
	{
		this.counter.setBitsPerCharacter(val);
	}

	/*
	 *	Handler for transmit text area events
	 */

	public void textValueChanged(TextEvent	ev)
	{
		if(this.useProtocolBox.getState())
		{
			return;
		}

		if (first && (this.text.getCaretPosition() > 0))
		{
			first = false;

			this.text.replaceRange("", 
					       0, 
					       this.text.getCaretPosition()
						 - 1);
		}

		if (!first && this.text.getText().endsWith("\n"))
		{
			this.sendData();
		}

	}

	public void run()
	{
		this.sendData();
	}

	public void sendString(String str)
	{
		int	count;

		count = str.length();

		if (count > 0)
		{
			try
			{
				owner.out.write(str.getBytes());
	
				counter.incrementValue((long) count);
	
				owner.ctlSigs.BE = false;
	
				owner.ctlSigs.showErrorValues();
			}
	
			catch (IOException ex)
			{
				if (owner.open)
				{
					System.out.println(owner.port.getName() 
							   + ": Cannot write to output stream");
	
					this.auto.setState(false);
				}
			}
		}
	}

	private void sendData()
	{
		if (this.owner.open && this.auto.getState())
		{
			while (this.owner.open && this.auto.getState())
			{
				sendString(testString);
			}
		}

		else
		{
			String str = this.text.getText();

			sendString(str);

			this.text.setText("");
		}
	} 

	/*
	 *	Handler for checkbox events
	 */

	public void itemStateChanged(ItemEvent	ev)
	{
		if (this.auto.getState() && (thr == null) && this.owner.open)
		{
			startTransmit();
		}
		else
		{
			stopTransmit();
		}

		if (this.sendBreak.getState())
		{
			if (this.owner.open)
			{
				this.sendBreak.setForeground(this.onColor);

				/*
				 *  Send a 1000 millisecond break.
				 */
	
				owner.port.sendBreak(1000);
			}

			this.sendBreak.setState(false);

			this.sendBreak.setForeground(this.offColor);
		}
		
		if(this.useProtocolBox.getState())
		{
			//Create a file chooser
			final JFileChooser fc = new JFileChooser();

			//In response to a button click:
			int returnVal = fc.showOpenDialog(this);
			
			if(returnVal == JFileChooser.APPROVE_OPTION)
			{
				this.text.setEnabled(false);

				try (BufferedReader reader = Files.newBufferedReader(fc.getSelectedFile().toPath()))
				{
					String line = null;
					StringBuilder builder = new StringBuilder();
					while ((line = reader.readLine()) != null)
					{
						builder.append(line);
						builder.append('\n');
					}
					first = false;
					this.text.setText(builder.toString());
				}
				catch (IOException x)
				{
					System.err.format("IOException: %s%n", x);
				}
			}
			else
			{
				this.useProtocolBox.setState(false);
			}
		}
		else
		{
			this.text.setEnabled(true);
		}
	}

	private void startTransmit()
	{
		if (thr == null);
		{
			this.auto.setForeground(this.onColor);
			counter.resetRate();

			thr = new Thread(this, "Xmt " + owner.port.getName());

			thr.start();
		}
	}

	public void stopTransmit()
	{
		thr = null;

		this.auto.setState(false);
		this.auto.setForeground(this.offColor);
	}

	public void actionPerformed(ActionEvent e)
	{
		if(this.useProtocolBox.getState())
		{
			if(e.getActionCommand() == runProtocolButton.getLabel())
			{
				if(runProtocolButton.getLabel() == "Run Protocol")
				{
					this.timer.start();
					this.runProtocolButton.setLabel("Stop Protocol");

					this.processNextProtocolItem();
				}
				else
				{
					this.stopTimer();
				}
			}
			else
			{
				counterValue--;
				timerLabel.setText("Next: " + String.valueOf(this.counterValue));

				if(counterValue < 1)
				{
					if(this.text.getText().trim().isEmpty())
					{
						this.stopTimer();
					}
					else
					{
						this.processNextProtocolItem();
					}
				}
			}
		}
	}

	private void processNextProtocolItem()
	{
		while(!this.text.getText().isEmpty())
		{
			String[] lines = this.text.getText().split("\n", 2);
			String[] instructions = lines[0].split(" Wait ");
			this.text.setText(lines[1]);

			this.sendString(instructions[0] + "\n");

			if(instructions.length > 1)
			{
				this.counterValue = Integer.parseInt(instructions[1]);
				this.timerLabel.setText("Next: " + String.valueOf(this.counterValue));
				break;
			}
			else
			{
				this.counterValue = 1;
				this.timerLabel.setText("Next: " + String.valueOf(this.counterValue));
			}
		}
	}
	private void stopTimer()
	{
		this.timer.stop();

		this.counterValue = 0;
		this.timerLabel.setText("Next: " + String.valueOf(this.counterValue));
		this.text.setText("");
		this.runProtocolButton.setLabel("Run Protocol");
		this.useProtocolBox.setState(false);
		this.text.setEnabled(true);
	}
}
