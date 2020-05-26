package com.github.noahr_atc.midisplitter;

/*
Copyright 2020 Noah Reeder

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

import javax.sound.midi.*;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * The GUI for {@link MidiSplitter}. This {@linkplain javax.swing Swing} GUI allows the user to
 * choose the input and output devices to perform note translations on, as well as to start and stop note translations.
 *
 * @author Noah Reeder
 * @version 1.0
 * @since 2020-01-28
 */
public class MidiSplitterForm {
    /**
     * Contains all UI elements of this {@code MidiSplitterForm}
     */
    public JPanel mainPanel;

    private JPanel selectionPanel;          // The JPanel containing everything except the refresh button
    private JPanel refreshButtonPanel;      // The JPanel containing the refresh buton
    private JComboBox<String> outputBox;    // The combobox for output device selection
    private JComboBox<String> inputBox;     // The combobox for input device selection
    private JButton refreshButton;          // The button for refreshing devices
    private JButton stopButton;             // The button to stop message splitting
    private JButton startButton;            // The button to start message splitting
    private LinkedHashMap<String, MidiDevice.Info> inputDevicesMap; // Inspired by MoppyControlGUI written by github.com/SammyIAm
    //                                      // ^ The hashmap used for mapping input device names to MidiDevice.Info objects
    private LinkedHashMap<String, MidiDevice.Info> outputDevicesMap;
    //                                      // ^ The hashmap used for mapping output device names to MidiDevice.Info objects
    private MidiDevice inputDevice;         // The MIDI device to receive MIDI messages from
    private MidiDevice outputDevice;        // The MIDI device to transmit MIDI messages to
    private Transmitter midiIn;             // The Transmitter instance received from inputDevice used for accessing MIDI messages
    private boolean firstManualRefresh;     // Boolean to know whether or not the lists have been manually refreshed yet
    private MidiProcessor processor;        // The MidiProcessor used to handle and split MIDI messages

    /**
     * Constructs a {@code MidiSplitterForm} using the specified lists to select the initial input and output MIDI devices.
     *
     * @param defaultInputDeviceList  this is the prioritized list of MidiDevice.Info objects to attempt to select as the default MIDI input device
     * @param defaultOutputDeviceList this is the prioritized list of MidiDevice.Info objects to attempt to select as the default MIDI output device
     */
    public MidiSplitterForm(List<MidiDevice.Info> defaultInputDeviceList, List<MidiDevice.Info> defaultOutputDeviceList) {
        // Define the listener for a refresh button press
        // End refreshButton->actionPerformed method
        refreshButton.addActionListener(e -> {
            // If the program is running on Windows, then due to a bug with MIDI device handling we must do some
            //      additional testing to see if the device is valid. In certain versions of Windows 7 and 8 (possibly
            //      earlier versions too), as well as Windows 10 at the time of writing this, when a MIDI device is plugged
            //      in it is added to the list retrieved by MidiSystem.getMidiDeviceInfo, but when a device is unplugged
            //      it is not removed until the program is restarted. Due to this, a user may be presented with ghost devices
            //      that were recently unplugged. In the C# program I made that this is based off of, I solved this by running
            //      a second program alongside my GUI and using IPC to retrieve the relevant MIDI information, simply restarting
            //      the console application during refreshes. This solution is significantly more difficult to implement in
            //      Java, and is prone to a larger variety of issues. I have instead opted to simply provide a warning to
            //      Windows users until this bug gets addressed.
            if (MidiSplitter.isWindows() && firstManualRefresh) {
                JOptionPane.showMessageDialog(
                        SwingUtilities.getAncestorOfClass(JFrame.class, mainPanel),
                        "<html><body><p style='width: 203px;'>" +
                                "Note: Due to a bug in Windows, MIDI devices that have been unplugged since the application " +
                                "started will still appear in the list and appear to be operational, although the device will " +
                                "obviously not transmit or receive any messages." +
                                "</p></body></html>",
                        "Note on Windows MIDI support",
                        JOptionPane.WARNING_MESSAGE
                ); // End showMessageDialog call
                firstManualRefresh = false;
            } // End if(isWindows && firstManualRefresh)
            refreshDeviceLists();
        }); // End refreshButton.addActionListener call

        // Define the listener for a start button press
        startButton.addActionListener((e) -> {
            try {
                startSplitting();
            } catch (MidiUnavailableException ex) {
                // No resource available, allow user to try again
                JOptionPane.showMessageDialog(
                        SwingUtilities.getAncestorOfClass(JFrame.class, mainPanel),
                        "<html><body><p style='width: 203px;'>" +
                                "Error accessing MIDI device." +
                                "</p></body></html>",
                        "MIDI Device Error",
                        JOptionPane.WARNING_MESSAGE
                ); // End showMessageDialog call
            } // End try {} catch(MidiUnavailableException)
            catch (IllegalArgumentException ex) {
                // No resource available, allow user to try again
                JOptionPane.showMessageDialog(
                        SwingUtilities.getAncestorOfClass(JFrame.class, mainPanel),
                        "<html><body><p style='width: 203px;'>" +
                                "Selected MIDI device removed, the list will now be refreshed." +
                                "</p></body></html>",
                        "MIDI Device Error",
                        JOptionPane.WARNING_MESSAGE
                ); // End showMessageDialog call
            } // End try {} catch(IllegalArgumentException)
        }); // End startButton.addActionListener call

        // Define the listener for a stop button press
        stopButton.addActionListener((e) -> stopSplitting());

        // Ensure the device lists are valid
        if (defaultInputDeviceList == null) { defaultInputDeviceList = new ArrayList<>(); }
        if (defaultOutputDeviceList == null) { defaultOutputDeviceList = new ArrayList<>(); }
        if (defaultInputDeviceList.isEmpty() || defaultOutputDeviceList.isEmpty()) {
            // Iterate over the list of MIDI devices until the first input and output devices are found, using those to construct the form
            // Note: Iteration is escaped as soon as both a transmitter and receiver are found
            MidiDevice.Info[] devices;
            devices = MidiSystem.getMidiDeviceInfo();
            for (MidiDevice.Info d : devices) {
                try {
                    MidiDevice device = MidiSystem.getMidiDevice(d);
                    // If the device has a transmitter, then it transmits to us and is therefore an input port
                    if (device.getMaxTransmitters() != 0) {
                        if (defaultInputDeviceList.isEmpty()) { defaultInputDeviceList.add(d); }
                        if (!defaultOutputDeviceList.isEmpty()) {break;}
                    } // End if(device.numTransmitters != 0)
                    // If the device has a receiver, then it receives from us and is therefore an output port
                    if (device.getMaxReceivers() != 0) {
                        if (defaultOutputDeviceList.isEmpty()) {defaultOutputDeviceList.add(d);}
                        if (!defaultInputDeviceList.isEmpty()) {break;}
                    } // End if(device.numReceivers != 0)
                } catch (MidiUnavailableException ignored) {} // Skip device if unavailable
            } // End for(d : devices)
        } // End if(defaultInputDeviceList.isEmpty || defaultOutputDeviceList.isEmpty)

        // Attempt to select the requested devices
        // Note: In the (unlikely) case that since launch all of the requested devices have been removed, we will simply
        //      use the default device
        for (MidiDevice.Info in : defaultInputDeviceList) {
            inputBox.setSelectedItem(in.getName());
            if (String.valueOf(inputBox.getSelectedItem()).equals(in.getName())) { // Check if device name was found in list
                break;
            } // Escape is selection was successful
        } // End for(in : defaultInputDeviceList)
        for (MidiDevice.Info out : defaultOutputDeviceList) {
            outputBox.setSelectedItem(out.getName());
            if (String.valueOf(outputBox.getSelectedItem()).equals(out.getName())) { // Check if device name was found in list
                break;
            } // Escape is selection was successful
        } // End for (out : defaultOutputDeviceList)

        // If both comboboxes are currently valid, enable the start button
        if (inputBox.getSelectedItem() != null && outputBox.getSelectedItem() != null) { startButton.setEnabled(true); }
    } // End constructor MidiSplitterForm(MidiDevice.Info, MidiDevice.Info)

    /**
     * Constructs a {@code MidiSplitterForm} with the first input and output MIDI devices selected.
     */
    public MidiSplitterForm() {
        MidiDevice.Info[] devices;
        MidiDevice.Info defaultInputDevice = null;
        MidiDevice.Info defaultOutputDevice = null;

        // Iterate over the list of MIDI devices until the first input and output devices are found, using those to construct the form
        // Note: Iteration is escaped as soon as both a transmitter and receiver are found
        devices = MidiSystem.getMidiDeviceInfo();
        for (MidiDevice.Info d : devices) {
            try {
                MidiDevice device = MidiSystem.getMidiDevice(d);
                // If the device has a transmitter, then it transmits to us and is therefore an input port
                if (device.getMaxTransmitters() != 0) {
                    defaultInputDevice = d;
                    if (defaultOutputDevice != null) {break;}
                } // End if(device.numTransmitters != 0)
                // If the device has a receiver, then it receives from us and is therefore an output port
                if (device.getMaxReceivers() != 0) {
                    defaultOutputDevice = d;
                    if (defaultInputDevice != null) {break;}
                } // End if(device.numReceivers != 0)
            } catch (MidiUnavailableException ignored) {} // Skip device if unavailable
        } // End for(d : devices)

        // Use the verbose constructor to create the MidiSplitterForm object
        new MidiSplitterForm(Collections.singletonList(defaultInputDevice), Collections.singletonList(defaultOutputDevice));
    } // End constructor MidiSplitterForm()

    /**
     * Simulates a play button press.
     */
    public void performPlayClick() { startButton.doClick(); }

    /**
     * Releases held resources. Ensure this is called upon form closure.
     */
    public void close() {
        if (processor != null) {
            processor.close();
            processor = null;
        } // End if(processor != null)
    } // End close method

    // The method for creating UI components. Called immediately upon construction
    private void createUIComponents() {
        // Initialize private variables here since the designer forces this to be the first method called in the constructor
        inputDevicesMap = new LinkedHashMap<>();
        outputDevicesMap = new LinkedHashMap<>();
        firstManualRefresh = true;
        processor = null;

        // Create the refresh button and adjust its margins
        refreshButton = new JButton();
        refreshButton.setMargin(new Insets(-5, 0, -2, 0));

        // Configure the MIDI I/O selection comboboxes and retrieve current MIDI devices
        inputBox = new JComboBox<>();
        outputBox = new JComboBox<>();
        inputBox.setPrototypeDisplayValue("X");
        outputBox.setPrototypeDisplayValue("X");
        refreshDeviceLists();
    } // End createUIComponents method

    // The method for refreshing the device comboboxes
    private void refreshDeviceLists() {
        // Save currently selected items
        String currentInputDevice = String.valueOf(inputBox.getSelectedItem());
        String currentOutputDevice = String.valueOf(outputBox.getSelectedItem());

        // Clear all current lists
        inputBox.removeAllItems();
        outputBox.removeAllItems();
        inputDevicesMap.clear();
        outputDevicesMap.clear();

        // Retrieve and iterate over the list of MIDI devices, adding to the hashmaps as necessary
        MidiDevice.Info[] devices = MidiSystem.getMidiDeviceInfo();
        for (MidiDevice.Info d : devices) {
            try {
                MidiDevice device = MidiSystem.getMidiDevice(d);
                // If the device has a transmitter, then it transmits to us and is therefore an input port
                if (device.getMaxTransmitters() != 0) { inputDevicesMap.put(d.getName(), d); }
                // If the device has a receiver, then it receives from us and is therefore an output port
                if (device.getMaxReceivers() != 0) { outputDevicesMap.put(d.getName(), d); }
            } catch (MidiUnavailableException ignored) {} // Skip device if unavailable
        } // End for (d : devices)

        // Add all hashmap keys to the comboboxes
        inputDevicesMap.keySet().forEach((i) -> inputBox.addItem(i));
        outputDevicesMap.keySet().forEach((i) -> outputBox.addItem(i));

        // Attempt to restore the selected devices
        // Note: If the previously selected device is not found, the selection will remain as the first entry
        if (currentInputDevice != null) { inputBox.setSelectedItem(currentInputDevice); }
        if (currentOutputDevice != null) { outputBox.setSelectedItem(currentOutputDevice); }
    } // End refreshDeviceLists method

    // The method for starting the MidiProcessor
    private void startSplitting() throws MidiUnavailableException, IllegalArgumentException {
        MidiProcessor newProcessor;

        // Ensure the input and output devices are closed and set to null
        if (inputDevice != null) {if (inputDevice.isOpen()) { inputDevice.close(); }}
        if (outputDevice != null) {if (outputDevice.isOpen()) { outputDevice.close(); }}
        midiIn = null;
        inputDevice = null;
        outputDevice = null;

        // Ensure an item is selected in both boxes
        if (inputBox.getSelectedItem() == null || outputBox.getSelectedItem() == null) {
            throw new IllegalArgumentException("null item in combobox");
        }

        // Attempt to pass the necessary information to the MIDI processor
        try {
            // Retrieve the input and output devices, and explicitly open the input device
            inputDevice = MidiSystem.getMidiDevice(inputDevicesMap.get(inputBox.getSelectedItem().toString()));
            outputDevice = MidiSystem.getMidiDevice(outputDevicesMap.get(outputBox.getSelectedItem().toString()));
            inputDevice.open();

            // Get the transmitter of the input device
            midiIn = inputDevice.getTransmitter();

            // Construct the new MIDI processor before overwriting the current processor. This is done because the new processor
            // construction is the last call that could generate an exception, so we want to ensure that the current processor
            // is preserved just in case the new processor construction raises an exception. We will also ensure that the previous
            // MIDI processor was closed
            // Note: Although we close the processor here, I strongly recommend closing it beforehand to avoid issues with
            //      creating a new MidiProcessor using the same output device due to the original MidiProcessor potentially
            //      holding a lock on it
            if (processor != null) { if (processor.isRunning()) { processor.close(); }}
            newProcessor = new MidiProcessor(outputDevice, MidiSplitter.debugMode());
            processor = newProcessor;

            // Set the transmitter to transmit to the processor
            midiIn.setReceiver(processor);
        } catch (MidiUnavailableException | IllegalArgumentException e) { // Handle both types of exception
            // If e is an IllegalArgumentException, then something about the requested devices has changed (e.g. it was
            // removed), so we need to refresh the lists)
            if (e instanceof IllegalArgumentException) {
                // Simulate pressing the refresh button so the Windows warning is displayed if necessary
                refreshButton.doClick();
            } // End if(e ∈ IllegalArgumentException)

            // Close input device if necessary and reset devices to null, forwarding the exception
            // Note: midiIn is also closed by inputDevice.close
            if (inputDevice != null) {if (inputDevice.isOpen()) { inputDevice.close(); }}
            midiIn = null;
            inputDevice = null;
            outputDevice = null;
            throw e;
        } // End try {} catch(Exception)

        // Disable the start and refresh buttons, and enable the stop button
        startButton.setEnabled(false);
        refreshButton.setEnabled(false);
        outputBox.setEnabled(false);
        inputBox.setEnabled(false);
        stopButton.setEnabled(true);
    } // End startSplitting method

    // The method for stopping the MidiProcessor
    private void stopSplitting() {
        // Shutdown the MIDI processor
        if (processor != null) {
            processor.close();
            processor = null;
        } // End if(processor != null)

        // Close input device if necessary and reset devices to null, forwarding the exception
        // Note: midiIn is also closed by inputDevice.close
        if (inputDevice != null) {if (inputDevice.isOpen()) { inputDevice.close(); }}
        midiIn = null;
        inputDevice = null;
        outputDevice = null;

        // Disable the stop button, and enable the start and refresh buttons
        stopButton.setEnabled(false);
        refreshButton.setEnabled(true);
        outputBox.setEnabled(true);
        inputBox.setEnabled(true);
        startButton.setEnabled(true);
    } // End stopSplittingMethod
} // End MidiSplitterForm class
