"use client";

import { useMemo, useState } from "react";
import { Check, ChevronsUpDown } from "lucide-react";
import { Avatar, AvatarFallback, Popover, PopoverContent, PopoverTrigger } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { User } from "@/lib/types";
import { cn } from "@/lib/utils";

/** Searchable single-select of users. */
export function UserCombobox({
  users,
  value,
  onChange,
  invalid,
}: {
  users: User[];
  value: string;
  onChange: (id: string) => void;
  invalid?: boolean;
}) {

  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");

  const selected = users.find((user) => user.id === value);
  const filtered = useMemo(() => {
    const needle = search.trim().toLowerCase();
    return needle ? users.filter((user) => user.username.toLowerCase().includes(needle)) : users;
  }, [users, search]);

  return (
    <Popover
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (!next) setSearch("");
      }}
    >
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="outline"
          role="combobox"
          aria-expanded={open}
          aria-invalid={invalid}
          className={cn(
            "h-10 w-full justify-between font-normal",
            !selected && "text-muted-foreground",
            invalid && "border-destructive",
          )}
        >
          <span className="truncate">{selected?.username ?? "Select a user..."}</span>
          <ChevronsUpDown className="opacity-60" />
        </Button>
      </PopoverTrigger>
      <PopoverContent>
        <Input
          autoFocus
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Search users..."
          aria-label="Search users..."
          className="mb-1 h-9"
        />
        <ul role="listbox" className="max-h-56 overflow-y-auto">
          {filtered.length === 0 ? (
            <li className="px-3 py-4 text-center text-sm text-muted-foreground">
              No users available to add.
            </li>
          ) : (
            filtered.map((user) => (
              <li key={user.id} role="option" aria-selected={user.id === value}>
                <button
                  type="button"
                  onClick={() => {
                    onChange(user.id);
                    setOpen(false);
                    setSearch("");
                  }}
                  className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm hover:bg-accent hover:text-accent-foreground"
                >
                  <Avatar className="size-6">
                    <AvatarFallback className="text-[10px]">{user.username.slice(0, 2)}</AvatarFallback>
                  </Avatar>
                  <span className="flex-1 truncate">{user.username}</span>
                  {user.id === value && <Check className="size-4" />}
                </button>
              </li>
            ))
          )}
        </ul>
      </PopoverContent>
    </Popover>
  );
}
