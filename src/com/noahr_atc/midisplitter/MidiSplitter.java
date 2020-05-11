package com.noahr_atc.midisplitter;

import org.jetbrains.annotations.NotNull;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Collections;
import java.util.Scanner;
import java.util.List;
import java.util.ArrayList;

/**
 * <h1>MIDI Splitter (Java)</h1>
 * <p>
 * This program receives MIDI messages from a transmitting MIDI port and splits any incoming notes (e.g. from a chord) into the
 * first available MIDI channel. This is based off of a similar application I wrote in C#, but I decided to do a Java rewrite
 * to make it available on operating systems other than Windows.
 * </p>
 * <p>
 * This program was designed to get around an issue with using MIDI keyboards in <a href="github.com/SammyIAm/Moppy2">Moppy2</a>,
 * but may be useful for other applications as well. Recommended software to use alongside this on
 * Windows<a href="#windows-midi-bug" style="font-size:smaller;vertical-align:super">1</a> is
 * <a href="https://www.tobias-erichsen.de/software/loopmidi.html">loopMIDI by Tobias Erichsen</a>. On most Linux kernels, a
 * virtual MIDI through-port is provided and can be enabled with {@code modprobe snd-virmidi snd_index=<index of last audio card +
 * 1>}.<a href="#linux-jvm-bug" style="font-size:smaller;vertical-align:super">2</a>
 * </p>
 * <hr>
 * <a id="windows-midi-bug"></a>
 * <span style="font-size:smaller;">1 - Windows users please note that due to a bug with MIDI device handling in Windows, when running this program on Windows a device will
 * not be removed when the lists are refreshed, however new devices will be added. The ghost device will disappear upon restarting the program.</span><br><br>
 * <a id="linux-jvm-bug"></a>
 * <span style="font-size:smaller;">2 - Linux users please note that due to an issue with JVM MIDI access, Java reports all sub-devices of the virmidi ports seperately.
 * I have found success in simply not using more than one virmidi port where the second digit (y) in the [hw: x,y,z] is the same.</span>
 *
 * @author Noah Reeder
 * @version 1.0
 * @since 2020-01-28
 */
public class MidiSplitter {
    private static boolean runningWindows; // Static boolean used to know if the computer running the program is on Windows. Visible to all classes in this package
    private static OutputMode outputMode = OutputMode.NORMAL;
    //                        // ^ Describes whether to output to console normally, quietly, silently, or with debug info. Visible to all classes in this package

    /**
     * The entrance method for the program.
     *
     * @param args the command line arguments specified.
     *             <ul>
     *                  <li><b>-h</b>, <b>-help</b>, <b>--help</b>
     *                          <ul>
     *                              <li>Display the help prompt</li>
     *                          </ul>
     *                  </li>
     *                  <li><b>-i</b> <i>device</i>, <b>--input</b> <i>device</i>
     *                          <ul>
     *                              <li>Specify a default MIDI input device to attempt to select</li>
     *                              <li>Sequential instances of argument represent prioritized default input devices</li>
     *                              <li>If both a valid --input and --output argument are provided, the splitter will start on launch</li>
     *                          </ul>
     *                  </li>
     *                  <li><b>-o</b> <i>device</i>, <b>--output</b> <i>device</i>
     *                          <ul>
     *                              <li>Specify a default MIDI output device to attempt to select</li>
     *                              <li>Sequential instances of argument represent prioritized default output devices</li>
     *                              <li>If both a valid --input and --output argument are provided, the splitter will start on launch</li>
     *                          </ul>
     *                  </li>
     *                  <li><b>-s</b>, <b>--silent</b>
     *                          <ul>
     *                              <li>Suppress all console output, including error messages</li>
     *                          </ul>
     *                  </li>
     *                  <li><b>-q</b>, <b>--quiet</b>
     *                          <ul>
     *                              <li>Show console error messages, but don't ask for user input; overridden by --silent</li>
     *                          </ul>
     *                  </li>
     *                  <li><b>-d</b>, <b>--debug</b>
     *                          <ul>
     *                              <li>Output MIDI translation debugging information to the console; overridden by --silent and --quiet</li>
     *                          </ul>
     *                  </li>
     *                  <li><b>--</b>
     *                          <ul>
     *                              <li>Don't interpret arguments inside '--' block as options (for example you could do '{@code --input -- -i --}'
     *                              if you had a device named '{@code -i}')</li>
     *                          </ul>
     *                  </li>
     *             </ul>
     *
     * @author Noah Reeder
     * @version 1.0
     * @since 2020-03-08
     */
    public static void main(String[] args) {
        ArrayList<MidiDevice.Info> inputDevices = new ArrayList<>();
        ArrayList<MidiDevice.Info> outputDevices = new ArrayList<>();
        MidiDevice.Info[] devices;
        Options options;
        MidiSplitterForm form;

        // Check if running on Windows; see MidiSplitterForm refresh code for reasoning
        runningWindows = System.getProperty("os.name").startsWith("Windows");

        // Enumerate MIDI devices
        devices = MidiSystem.getMidiDeviceInfo();
        for (MidiDevice.Info d : devices) {
            try {
                MidiDevice device = MidiSystem.getMidiDevice(d);
                // If the device has a transmitter, then it transmits to us and is therefore an input port
                if (device.getMaxTransmitters() != 0) { inputDevices.add(d); }
                // If the device has a receiver, then it receives from us and is therefore an output port
                if (device.getMaxReceivers() != 0) { outputDevices.add(d); }
            } catch (MidiUnavailableException ignored) {} // Skip device if unavailable
        } // End for(d : devices)

        // Create the main frame, setting minimum dimensions and standard properties
        JFrame frame = new JFrame("MIDI Splitter");

        options = ParseArguments(args, inputDevices, outputDevices);

        // Using the selected default devices, construct the form and set the frame properties
        form = new MidiSplitterForm(options.defaultInputDeviceList, options.defaultOutputDeviceList);
        frame.setContentPane(form.mainPanel);
        frame.setMinimumSize(new Dimension(346, 98));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                form.close();
                super.windowClosing(e);
            }
        }); // End frame.addWindowListener call
        frame.pack();
        frame.setVisible(true);
        if (options.launchStarted) { form.performPlayClick(); }
    } // End main method

    static boolean isWindows() { return runningWindows; }

    static boolean debugMode() { return (outputMode == OutputMode.DEBUG); }

    private static Options ParseArguments(@NotNull String[] args, @NotNull List<MidiDevice.Info> inputDevices, @NotNull List<MidiDevice.Info> outputDevices) {
        ArrayList<MidiDevice.Info> defaultInputDeviceList = new ArrayList<>();
        ArrayList<MidiDevice.Info> defaultOutputDeviceList = new ArrayList<>();
        boolean interpretOptions = true;
        boolean expectArgumentNext = false;
        boolean inputDeviceRequested = false;
        boolean outputDeviceRequested = false;
        ArrayList<String[]> commands = new ArrayList<>();

        // Prior to parsing all options, scan for quiet, silent, or debug modes
        for (String a : args) {
            // If '--' option is encountered, flip whether options are interpreted and continue to next argument
            if (a.equals("--")) {interpretOptions = !interpretOptions; continue;}

            // Since silent mode overrides quiet mode, first check if it is already enabled and enable it if requested
            if (interpretOptions && outputMode != OutputMode.SILENT) {
                if (a.equals("-s") || a.equals("--silent")) { outputMode = OutputMode.SILENT; }

                // If silent mode is not enabled, check if quiet mode is enabled and enable it if requested
                if (outputMode != OutputMode.QUIET) {
                    if (a.equals("-q") || a.equals("--quiet")) { outputMode = OutputMode.QUIET; }

                    // If neither silent nor quiet mode are enabled, enable debug mode if requested
                    if (a.equals("-d") || a.equals("--debug")) { outputMode = OutputMode.DEBUG; }
                } // End if(!OutputMode.QUIET)
            } // End if(interpretOptions && !OutputMode.SILENT)
        } // End for(a : args)

        // Ensure "--" options are matched
        if (!interpretOptions) {
            // Check for silent mode (no output allowed)
            if (outputMode == OutputMode.SILENT) { System.exit(ExitCodes.INVALID_ARGUMENTS); }
            System.out.print("MidiSplitter: unmatched '--' option");
            System.exit(ExitCodes.INVALID_ARGUMENTS);
        } // End if(!interpretOptions)

        // Strip arguments into constituent commands, removing unnecessary options ('--', '-s', '-q')
        // Note: Probably could be refactored out, but even with this thinking about argument processing is making my head hurt
        for (String a : args) {
            // If '--' option is encountered, flip whether options are interpreted and continue to next argument
            if (a.equals("--")) {
                interpretOptions = !interpretOptions;
                continue;
            } // End if(a == "--")
            if (interpretOptions) {
                // Interpret current argument
                switch (a) {
                    case "-h":
                    case "-help":
                    case "--help": {
                        // Error if an argument is expected, otherwise carry on
                        if (expectArgumentNext) {
                            // Check for silent mode (no output allowed)
                            if (outputMode == OutputMode.SILENT) { System.exit(ExitCodes.INVALID_ARGUMENTS); }
                            System.out.print(
                                    // We're cheating a little bit here - the only commands requiring arguments are '--input'
                                    //      and '--output', so we can use a generic help message for both of them
                                    // Note: 'commands.size() - 1' can't be out-of-bounds because there must have been a command
                                    //      for expectArgumentNext to be true
                                    "MidiSplitter: option '" + commands.get(commands.size() - 1)[0] + "' requires an argument, option '"
                                            + a + "' was specified instead\n" +
                                            "Usage: MidiSplitter [OPTIONS]... " + commands.get(commands.size() - 1)[0] + " \033[3mdevice\033[0m...\n" +
                                            "Use 'MidiSplitter --help' for additional information"
                            );
                            System.exit(ExitCodes.INVALID_ARGUMENTS);
                        } // End if(expectArgumentNext)
                        commands.add(new String[]{a, ""});
                        break;
                    } // End "--help", "--debug" cases
                    case "-i":
                    case "--input":
                    case "-o": // Handling here is the same for both --input and --output flags
                    case "--output": {
                        // Error if an argument is expected, otherwise carry on
                        if (expectArgumentNext) {
                            // Check for silent mode (no output allowed)
                            if (outputMode == OutputMode.SILENT) { System.exit(ExitCodes.INVALID_ARGUMENTS); }
                            System.out.print(
                                    // We're cheating a little bit here - the only commands requiring arguments are '--input'
                                    //      and '--output', so we can use a generic help message for both of them
                                    // Note: 'commands.size() - 1' can't be out-of-bounds because there must have been a command
                                    //      for expectArgumentNext to be true
                                    "MidiSplitter: option '" + commands.get(commands.size() - 1)[0] + "' requires an argument, option '"
                                            + a + "' was specified instead\n" +
                                            "Usage: MidiSplitter [OPTIONS]... " + commands.get(commands.size() - 1)[0] + " \033[3mdevice\033[0m...\n" +
                                            "Use 'MidiSplitter --help' for additional information"
                            );
                            System.exit(ExitCodes.INVALID_ARGUMENTS);
                        } // End if(expectArgumentNext)
                        commands.add(new String[]{a, ""});
                        expectArgumentNext = true;
                        break;
                    } // End "--output" case
                    case "-q":
                    case "--quiet":
                    case "-s":
                    case "--silent":
                    case "-d":
                    case "--debug": {
                        // Error if an argument is expected, otherwise carry on
                        if (expectArgumentNext) {
                            // Check for silent mode (no output allowed)
                            if (outputMode == OutputMode.SILENT) { System.exit(ExitCodes.INVALID_ARGUMENTS); }
                            System.out.print(
                                    // We're cheating a little bit here - the only commands requiring arguments are '--input'
                                    //      and '--output', so we can use a generic help message for both of them
                                    // Note: 'commands.size() - 1' can't be out-of-bounds because there must have been a command
                                    //      for expectArgumentNext to be true
                                    "MidiSplitter: option '" + commands.get(commands.size() - 1)[0] + "' requires an argument, option '"
                                            + a + "' was specified instead\n" +
                                            "Usage: MidiSplitter [OPTIONS]... " + commands.get(commands.size() - 1)[0] + " \033[3mdevice\033[0m...\n" +
                                            "Use 'MidiSplitter --help' for additional information"
                            );
                            System.exit(ExitCodes.INVALID_ARGUMENTS);
                        } // End if(expectArgumentNext)
                        break; // Already processed, no sense in adding to to the list of commands to be processed
                    } // End "--silent" case
                    default: {
                        // If an argument is expected, add it to the list, otherwise throw an error
                        // Note: 'commands.size() - 1' can't be out-of-bounds because there must have been a command
                        //      for expectArgumentNext to be true
                        if (expectArgumentNext) {
                            commands.get(commands.size() - 1)[1] = a;
                            expectArgumentNext = false;
                        } // End if(expectArgumentNext)
                        else {
                            // Check for silent mode (no output allowed)
                            if (outputMode == OutputMode.SILENT) { System.exit(ExitCodes.INVALID_ARGUMENTS); }
                            System.out.print("Unexpected argument '" + a + "', terminating");
                            System.exit(ExitCodes.INVALID_ARGUMENTS);
                        } // End if(expectArgumentNext) {} else
                    } // End default case
                } // End switch(a)
            } // End if(interpretOptions)
            else {
                // What-you-see-is-what-you-get argument mode enabled, skip option processing and go straight to argument
                // If an argument is expected, add it to the list, otherwise throw an error
                // Note: 'commands.size() - 1' can't be out-of-bounds because there must have been a command
                //      for expectArgumentNext to be true
                if (expectArgumentNext) {
                    commands.get(commands.size() - 1)[1] = a;
                    expectArgumentNext = false;
                } // End if(expectArgumentNext)
                else {
                    // Check for silent mode (no output allowed)
                    if (outputMode == OutputMode.SILENT) { System.exit(ExitCodes.INVALID_ARGUMENTS); }
                    System.out.print("Unexpected argument '" + a + "', terminating");
                    System.exit(ExitCodes.INVALID_ARGUMENTS);
                } // End if(expectedArgumentNext) {} else
            } // End if(interpretOptions) {} else
        } // End for(a : args)

        // Ensure we aren't missing an argument
        if (expectArgumentNext) {
            // Check for silent mode (no output allowed)
            if (outputMode == OutputMode.SILENT) { System.exit(ExitCodes.INVALID_ARGUMENTS); }
            System.out.print(
                    // We're cheating a little bit here - the only commands requiring arguments are '--input'
                    //      and '--output', so we can use a generic help message for both of them
                    // Note: 'commands.size() - 1' can't be out-of-bounds because there must have been a command
                    //      for expectArgumentNext to be true
                    "MidiSplitter: option '" + commands.get(commands.size() - 1)[0] + "' requires an argument\n" +
                            "Usage: MidiSplitter [OPTIONS]... " + commands.get(commands.size() - 1)[0] + " \033[3mdevice\033[0m...\n" +
                            "Use 'MidiSplitter --help' for additional information"
            );
            System.exit(ExitCodes.INVALID_ARGUMENTS);
        } // End if(expectArgumentNext)

        // Parse the rest of the command-line options
        for (String[] c : commands) {
            switch (c[0]) {
                case "-h":
                case "-help":
                case "--help": // Display help menu
                {
                    // Check for silent mode (no output allowed)
                    if (outputMode == OutputMode.SILENT) { System.exit(ExitCodes.INVALID_ARGUMENTS); }
                    System.out.print(
                            /*
                            To be enabled upon worldwide adoption of consoles which support ANSI escape sequences
                            *cough* replace Windows Console with Windows Terminal *cough*

                            "\033[1m-h\033[0m, \033[1m-help\033[0m, \033[1m--help\033[0m\n" +
                                    "\tDisplay this help prompt\n" +
                                    "\033[1m-i\033[0m \033[3mdevice\033[0m, \033[1m--input\033[0m \033[3mdevice\033[0m\n" +
                                    "\tSpecify a default MIDI input device to attempt to select\n" +
                                    "\033[1m-o\033[0m \033[3mdevice\033[0m, \033[1m--output\033[0m \033[3mdevice\033[0m\n" +
                                    "\tSpecify a default MIDI output device to attempt to select\n" +
                                    "\033[1m-s\033[0m, \033[1m--silent\033[0m\n" +
                                    "\tSuppress all console output, including error messages\n" +
                                    "\033[1m-q\033[0m, \033[1m--quiet\033[0m\n" +
                                    "\tShow console error messages, but don't ask for user input; overridden by --silent\n" +
                                    "\033[1m-d\033[0m, \033[1m--debug\033[0m\n" +
                                    "\tOutput MIDI translation debugging information to the console; overridden by --silent or --quiet\n" +
                                    "\033[1m--\033[0m\n" +
                                    "\tDon't interpret arguments inside '--' block as options (for example if for some reason device " +
                                    "name is --input)"
                            */
                            "-h, -help, --help\n" +
                                    "\tDisplay this help prompt\n" +
                                    "-i device, --input device\n" +
                                    "\tSpecify a default MIDI input device to attempt to select\n" +
                                    "\tIf this is specified more than once, it acts as a prioritized list with the first having priority\n" +
                                    "\tIf this is specified with --output then the splitter is started upon launch\n" +
                                    "-o device, --output device\n" +
                                    "\tSpecify a default MIDI output device to attempt to select\n" +
                                    "\tIf this is specified more than once, it acts as a prioritized list with the first having priority\n" +
                                    "\tIf this is specified with --input then the splitter is started upon launch\n" +
                                    "-s, --silent\n" +
                                    "\tSuppress all console output, including error messages\n" +
                                    "-q, --quiet\n" +
                                    "\tShow console error messages, but don't ask for user input; overridden by --silent\n" +
                                    "-d, --debug\n" +
                                    "\tOutput MIDI translation debugging information to the console; overridden by --silent or --quiet\n" +
                                    "--\n" +
                                    "\tDon't interpret arguments inside '--' block as options (for example if for some reason device name is --input)\n"
                    );
                    System.exit(ExitCodes.NORMAL);
                } // End "--help" case
                case "-i":
                case "--input": // Add default input device
                {
                    // Toggle that a specific input device was requested
                    if (!inputDeviceRequested) {inputDeviceRequested = true;}
                    // Iterate through the available input devices until the requested one is found or list exhausted
                    for (MidiDevice.Info in : inputDevices) {
                        if (in.getName().equals(c[1])) { defaultInputDeviceList.add(in); }
                    } // End for(in : inputDevices)
                    break;
                } // End "--input" case
                case "-o":
                case "--output": {
                    // Toggle that a specific output device was requested
                    if (!outputDeviceRequested) {outputDeviceRequested = true;}
                    // Iterate through the available output devices until the requested one is found or list exhausted
                    for (MidiDevice.Info out : outputDevices) {
                        if (out.getName().equals(c[1])) { defaultOutputDeviceList.add(out); }
                    } // End for(in : inputDevices)
                    break;
                } // End "--output" case
                case "-d":
                case "--debug":
            } // End switch(c[0])
        } // End for (c : commands)

        // Check if an input/output device was requested but none were found, in which case if allowed we will give an
        //      error and ask if the user would like to use the default device
        if (inputDeviceRequested && defaultInputDeviceList.isEmpty()) {
            // Check for silent mode (no output allowed)
            if (outputMode == OutputMode.SILENT) { System.exit(ExitCodes.INVALID_INPUT_DEVICE); }
            System.out.print("MidiSplitter: --input: None of the requested MIDI input devices not found\n");

            // Check for silent mode (no input allowed)
            if (outputMode == OutputMode.QUIET) { System.exit(ExitCodes.INVALID_INPUT_DEVICE); }
            // Ask if the user wants to use the default device and process response
            System.out.print("Would you like to use the default input device instead? [Y/n] ");
            Scanner s = new Scanner(System.in);
            switch (s.next()) {
                case "y":
                case "Y:":
                    // Allow the constructor call logic/following --input options to assign default device
                    break;
                case "n":
                case "N":
                    // Default device not wanted, quit with error code instead
                    System.exit(ExitCodes.INVALID_INPUT_DEVICE);
                default:
                    // Invalid response, terminate
                    System.out.print("MidiSplitter: Invalid response, terminating");
                    System.exit(ExitCodes.INVALID_RESPONSE);
            } // End switch(s.next)
        } // End if(!defaultInputDeviceList)
        if (outputDeviceRequested && defaultOutputDeviceList.isEmpty()) {
            // Check for silent mode (no output allowed)
            if (outputMode == OutputMode.SILENT) { System.exit(ExitCodes.INVALID_OUTPUT_DEVICE); }
            System.out.print("MidiSplitter: --output: None of the requested MIDI output devices not found\n");

            // Check for silent mode (no input allowed)
            if (outputMode == OutputMode.QUIET) { System.exit(ExitCodes.INVALID_OUTPUT_DEVICE); }
            // Ask if the user wants to use the default device and process response
            System.out.print("Would you like to use the default output device instead? [Y/n] ");
            Scanner s = new Scanner(System.in);
            switch (s.next()) {
                case "y":
                case "Y:":
                    // Allow the constructor call logic/following --output options to assign default device
                    break;
                case "n":
                case "N":
                    // Default device not wanted, quit with error code instead
                    System.exit(ExitCodes.INVALID_OUTPUT_DEVICE);
                default:
                    // Invalid response, terminate
                    System.out.print("MidiSplitter: Invalid response, terminating");
                    System.exit(ExitCodes.INVALID_RESPONSE);
            } // End switch(scanner.next)
        } // End if(!defaultOutputDeviceList)

        // Construct and return the Options object containing the parsed information
        return new Options(
                outputMode,
                (defaultInputDeviceList.isEmpty()) ? Collections.singletonList(inputDevices.get(0)) : defaultInputDeviceList,
                (defaultOutputDeviceList.isEmpty()) ? Collections.singletonList(outputDevices.get(0)) : defaultOutputDeviceList,
                (!defaultInputDeviceList.isEmpty() && !defaultOutputDeviceList.isEmpty())
        );
    } // End ParseArguments method

    /**
     * Defines the different exit codes of the application.
     */
    @SuppressWarnings("JavaDoc")
    public static class ExitCodes {
        /**
         * No error conditions; equal to {@value #NORMAL}.
         */
        public static final int NORMAL = 0;

        /**
         * An invalid response to a prompt lead to termination; equal to {@value #INVALID_RESPONSE}
         */
        public static final int INVALID_RESPONSE = 1;

        /**
         * An invalid argument lead to termination; equal to {@value #INVALID_ARGUMENTS}
         */
        public static final int INVALID_ARGUMENTS = 2;

        /**
         * An invalid input device was requested and lead to termination; equal to {@value #INVALID_INPUT_DEVICE}
         */
        public static final int INVALID_INPUT_DEVICE = 3;

        /**
         * An invalid output device was requested and lead to termination; equal to {@value #INVALID_OUTPUT_DEVICE}
         */
        public static final int INVALID_OUTPUT_DEVICE = 4;

        // Disable the constructor
        private ExitCodes() {}
    } // End ExitCodes class

    // The different output modes of the application
    private enum OutputMode {
        NORMAL,
        QUIET,
        SILENT,
        DEBUG
    } // End enum OutputMode

    // The object containing the options calculated by the argument processor
    private static class Options {
        public OutputMode outputMode;
        public List<MidiDevice.Info> defaultInputDeviceList;
        public List<MidiDevice.Info> defaultOutputDeviceList;
        public boolean launchStarted;

        /**
         * Verbose constructor. Assigns properties as passed.
         *
         * @param outputMode           Represents whether console output should be normal, quiet, or silent
         * @param defaultInputDevices  The list of default MIDI device to attempt to select for input, in order of priority
         * @param defaultOutputDevices The list of default MIDI device to to attempt to select for output, in order of priority
         * @param launchStarted        whether to start the MIDI processor upon launch or to wait for the user to press start
         */
        public Options(OutputMode outputMode, List<MidiDevice.Info> defaultInputDevices, List<MidiDevice.Info> defaultOutputDevices, boolean launchStarted) {
            this.outputMode = outputMode;
            this.defaultInputDeviceList = defaultInputDevices;
            this.defaultOutputDeviceList = defaultOutputDevices;
            this.launchStarted = launchStarted;
        } // End verbose Options constructor

        /**
         * Default constructor. Assigns the output mode to normal, devices to null, and to not launch started.
         */
        public Options() { new Options(OutputMode.NORMAL, null, null, false); }
    } // End Options class
} // End class MidiSplitter
