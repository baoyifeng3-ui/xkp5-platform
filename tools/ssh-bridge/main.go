package main

import (
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"
)

type session struct {
	id     string
	cmd    *exec.Cmd
	input  io.WriteCloser
	mu     sync.Mutex
	output []byte
	cursor int64
	closed bool
}
type server struct {
	mu       sync.Mutex
	sessions map[string]*session
	key      string
}

func main() {
	root := filepath.Join(os.Getenv("ProgramData"), "XKP5", "ssh")
	_ = os.MkdirAll(root, 0700)
	key := filepath.Join(root, "id_ed25519")
	if _, e := os.Stat(key); os.IsNotExist(e) {
		c := exec.Command("ssh-keygen.exe", "-t", "ed25519", "-N", "", "-f", key, "-C", "xkp5-platform")
		if out, e := c.CombinedOutput(); e != nil {
			panic(fmt.Sprintf("ssh-keygen: %s %v", out, e))
		}
	}
	s := &server{sessions: map[string]*session{}, key: key}
	mux := http.NewServeMux()
	mux.HandleFunc("/public-key", s.publicKey)
	mux.HandleFunc("/sessions", s.create)
	mux.HandleFunc("/sessions/", s.route)
	token := os.Getenv("XKP_SSH_BRIDGE_TOKEN")
	if token == "" {
		panic("XKP_SSH_BRIDGE_TOKEN is required")
	}
	secured := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("X-Bridge-Token") != token {
			http.Error(w, "unauthorized", 401)
			return
		}
		mux.ServeHTTP(w, r)
	})
	httpServer := &http.Server{Addr: "0.0.0.0:19245", Handler: secured, ReadHeaderTimeout: 5 * time.Second}
	fmt.Println("XKP SSH bridge listening on 0.0.0.0:19245")
	panic(httpServer.ListenAndServe())
}

func (s *server) publicKey(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	if r.Method != "GET" {
		http.Error(w, "method", 405)
		return
	}
	data, e := os.ReadFile(s.key + ".pub")
	if e != nil {
		http.Error(w, e.Error(), 500)
		return
	}
	json.NewEncoder(w).Encode(map[string]string{"publicKey": strings.TrimSpace(string(data))})
}
func (s *server) create(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	if r.Method != "POST" {
		http.Error(w, "method", 405)
		return
	}
	var q struct {
		Host string `json:"host"`
		User string `json:"user"`
	}
	if json.NewDecoder(io.LimitReader(r.Body, 4096)).Decode(&q) != nil || net.ParseIP(q.Host) == nil {
		http.Error(w, "invalid", 400)
		return
	}
	if q.User == "" {
		q.User = "root"
	}
	id := strconv.FormatInt(time.Now().UnixNano(), 36)
	cmd := exec.Command("ssh.exe", "-tt", "-i", s.key, "-o", "BatchMode=yes", "-o", "StrictHostKeyChecking=accept-new", "-o", "ConnectTimeout=10", q.User+"@"+q.Host)
	in, _ := cmd.StdinPipe()
	out, _ := cmd.StdoutPipe()
	errOut, _ := cmd.StderrPipe()
	item := &session{id: id, cmd: cmd, input: in}
	if e := cmd.Start(); e != nil {
		http.Error(w, e.Error(), 502)
		return
	}
	s.mu.Lock()
	s.sessions[id] = item
	s.mu.Unlock()
	go item.capture(out)
	go item.capture(errOut)
	go func() { _ = cmd.Wait(); item.mu.Lock(); item.closed = true; item.mu.Unlock() }()
	json.NewEncoder(w).Encode(map[string]string{"sessionId": id})
}
func (s *server) route(w http.ResponseWriter, r *http.Request) {
	parts := strings.Split(strings.TrimPrefix(r.URL.Path, "/sessions/"), "/")
	s.mu.Lock()
	item := s.sessions[parts[0]]
	s.mu.Unlock()
	if item == nil {
		http.Error(w, "missing", 404)
		return
	}
	if len(parts) == 1 && r.Method == "DELETE" {
		_ = item.input.Close()
		if item.cmd.Process != nil {
			_ = item.cmd.Process.Kill()
		}
		s.mu.Lock()
		delete(s.sessions, item.id)
		s.mu.Unlock()
		w.WriteHeader(204)
		return
	}
	if len(parts) != 2 {
		http.Error(w, "path", 404)
		return
	}
	switch parts[1] {
	case "input":
		var q struct {
			Data string `json:"data"`
		}
		if r.Method != "POST" || json.NewDecoder(io.LimitReader(r.Body, 8192)).Decode(&q) != nil {
			http.Error(w, "invalid", 400)
			return
		}
		_, e := io.WriteString(item.input, q.Data)
		if e != nil {
			http.Error(w, e.Error(), 409)
			return
		}
		w.WriteHeader(204)
	case "output":
		w.Header().Set("Content-Type", "application/json")
		cursor, _ := strconv.ParseInt(r.URL.Query().Get("cursor"), 10, 64)
		item.mu.Lock()
		start := cursor - item.cursor
		if start < 0 {
			start = 0
		}
		if start > int64(len(item.output)) {
			start = int64(len(item.output))
		}
		data := append([]byte(nil), item.output[start:]...)
		next := item.cursor + int64(len(item.output))
		closed := item.closed
		item.mu.Unlock()
		json.NewEncoder(w).Encode(map[string]interface{}{"data": string(data), "nextCursor": next, "closed": closed})
	default:
		http.Error(w, "path", 404)
	}
}
func (s *session) capture(r io.Reader) {
	buf := make([]byte, 4096)
	for {
		n, e := r.Read(buf)
		if n > 0 {
			s.mu.Lock()
			s.output = append(s.output, buf[:n]...)
			if len(s.output) > 1<<20 {
				drop := len(s.output) - (1 << 20)
				s.output = s.output[drop:]
				s.cursor += int64(drop)
			}
			s.mu.Unlock()
		}
		if e != nil {
			return
		}
	}
}
