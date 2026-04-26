package quotely

import "testing"

func TestTrimFullNameBeforePipe(t *testing.T) {
	tests := []struct {
		name     string
		fullName string
		want     string
	}{
		{
			name:     "name with pipe",
			fullName: "Alice Bob | very long suffix",
			want:     "Alice Bob",
		},
		{
			name:     "name without pipe",
			fullName: "Alice Bob",
			want:     "Alice Bob",
		},
		{
			name:     "empty name",
			fullName: "",
			want:     "",
		},
		{
			name:     "pipe at beginning",
			fullName: "| very long suffix",
			want:     "",
		},
		{
			name:     "multiple pipes",
			fullName: "Alice | Bob | Carol",
			want:     "Alice",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := trimFullNameBeforePipe(tt.fullName)
			if got != tt.want {
				t.Fatalf("trimFullNameBeforePipe(%q) = %q, want %q", tt.fullName, got, tt.want)
			}
		})
	}
}
