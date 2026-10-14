package main

import "testing"

func TestExecutableNameIsPlatformSpecific(t *testing.T) {
	if got := executableName("linux", "ssh"); got != "ssh" {
		t.Fatalf("linux executable = %q, want ssh", got)
	}
	if got := executableName("windows", "ssh"); got != "ssh.exe" {
		t.Fatalf("windows executable = %q, want ssh.exe", got)
	}
}

func TestBridgeRootUsesConfiguredStateDirectory(t *testing.T) {
	if got := bridgeRoot("/state/xkp", "linux", ""); got != "/state/xkp" {
		t.Fatalf("configured bridge root = %q", got)
	}
}

func TestBridgeRootHasLinuxDefault(t *testing.T) {
	if got := bridgeRoot("", "linux", ""); got != "/var/lib/xkp/ssh" {
		t.Fatalf("linux bridge root = %q", got)
	}
}
