using System;
using System.Threading;
using OpenHardwareMonitor.Hardware;

class TempReader {
    static void Main() {
        Console.WriteLine("Initializing...");
        Computer computer = new Computer();
        computer.CPUEnabled = true;
        computer.GPUEnabled = true;
        computer.MainboardEnabled = true;
        computer.FanControllerEnabled = true;
        
        Console.WriteLine("Opening...");
        computer.Open();
        Thread.Sleep(3000);
        
        Console.WriteLine("Hardware count: " + computer.Hardware.Length);
        double cpuTemp = -1;
        
        for (int attempt = 0; attempt < 5; attempt++) {
            Console.WriteLine("Attempt " + (attempt+1));
            foreach (IHardware hw in computer.Hardware) {
                Console.WriteLine("  Hardware: " + hw.Name + " Type: " + hw.HardwareType);
                hw.Update();
                Thread.Sleep(500);
                
                foreach (ISensor sensor in hw.Sensors) {
                    if (sensor.SensorType == SensorType.Temperature) {
                        Console.WriteLine("    TEMP: " + sensor.Name + " = " + sensor.Value + " C");
                        if (sensor.Value.HasValue) {
                            cpuTemp = Math.Max(cpuTemp, sensor.Value.Value);
                        }
                    }
                }
                
                foreach (IHardware sub in hw.SubHardware) {
                    sub.Update();
                    Console.WriteLine("    SubHardware: " + sub.Name);
                    foreach (ISensor sensor in sub.Sensors) {
                        if (sensor.SensorType == SensorType.Temperature) {
                            Console.WriteLine("      SUB TEMP: " + sensor.Name + " = " + sensor.Value + " C");
                            if (sensor.Value.HasValue) {
                                cpuTemp = Math.Max(cpuTemp, sensor.Value.Value);
                            }
                        }
                    }
                }
            }
            Thread.Sleep(1000);
        }
        
        computer.Close();
        Console.WriteLine("Final CPU Temp: " + cpuTemp + " C");
    }
}
