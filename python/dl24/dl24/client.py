import asyncio
import logging
import functools
import inspect
import re


class Failure(RuntimeError):
    """A FAILED response from the server"""
    def __init__(self, errno, strerror):
        super(Failure, self).__init__()
        self.errno = errno
        self.strerror = strerror

    def __str__(self):
        return '[{}] {}'.format(self.errno, self.strerror)


class ProtocolError(RuntimeError):
    """An error in parsing the protocol from the server"""
    def __init__(self, message, data):
        super(ProtocolError, self).__init__()
        self.message = message
        self.data = data

    def __str__(self):
        return 'Failed to parse "{}": {}'.format(self.data, self.message)


def command(name):
    """Decorator for a command that takes care of sending the command to the
    server and waiting for the OK response. The wrapped function is
    responsible for parsing the rest of the reply (if any).

    If the function takes keyword-only arguments, they are *not* passed to
    the server.
    """
    def decorator(func):
        signature = inspect.signature(func)
        @functools.wraps(func)
        async def wrapper(self, *args, **kwargs):
            bind = signature.bind(self, *args, **kwargs)
            bind.apply_defaults()
            fields = [name]
            for arg in bind.args[1:]:
                fields.append(str(arg))
            self.writeline(' '.join(fields))
            await self.read_ok()
            return await func(*bind.args, **bind.kwargs)
        return wrapper
    return decorator


class ClientBase(object):
    """A base class for clients wrapping a network protocol.

    Instances should not be created directly; instead use the asynchronous
    class method :meth:`create`.
    """

    def __init__(self):
        pass

    async def readline(self, expected=None, parse_failed=False):
        """Retrieves a single line, with the trailing newline removed.

        Parameters
        ----------
        expected : str or regex, optional
            If given, raise a :exc:`ProtocolError` if the read value is different
        parse_failed : bool, optional
            If true, raise a :exc:`Failure` if the line starts with FAILED

        Raises
        ------
        Failure
            if the line is a FAILED message and `parse_failed` is true
        ProtocolError
            if the line does not match `expected`
        EOFError
            if the server disconnected before sending anything
        """
        line = await self.reader.readline()
        line = line.decode('utf-8', errors='replace')
        if line == '':
            raise EOFError()
        line = line.rstrip()
        if parse_failed and line.startswith('FAILED '):
            fields = line.split(' ', 2)
            try:
                errno = int(fields[1])
                message = fields[2]
            except Exception as error:
                raise ProtocolError(str(error), line)
            raise Failure(errno, message)
        if hasattr(expected, 'match'):
            if not expected.fullmatch(line):
                raise ProtocolError('expected line matching {}'.format(expected.pattern), line)
        elif expected is not None:
            if line != expected:
                raise ProtocolError('expected {}'.format(expected), line)
        return line

    async def read_ok(self):
        """Read a line and expect that it is OK, otherwise raising a :exc:`Failure`"""
        return await self.readline(expected='OK', parse_failed=True)

    def writeline(self, line):
        """Writes a line to the socket, adding a newline."""
        data = (line + '\n').encode('utf-8')
        self.writer.write(data)

    @classmethod
    async def create(cls, host, port, login, password):
        self = cls()
        self.reader, self.writer = await asyncio.open_connection(
            host, port, limit=2**20)
        # Do the login protocol
        await self.readline(expected='LOGIN')
        self.writeline(login)
        await self.readline(expected='PASS')
        self.writeline(password)
        await self.read_ok()
        return self

    def close(self):
        self.writer.close()

    @command('WAIT')
    async def wait(self):
        await self.readline(expected=re.compile('WAITING .*'))
        await self.read_ok()
